import ipaddress
import json
import os
import pathlib
import re
import socket
import sqlite3
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any, Callable, Dict, List, Optional

from models import ExecutionContext, ToolCallTrace


@dataclass
class ToolSpec:
    name: str
    required_scope: str
    timeout_ms: int
    retry_max: int
    schema_required: List[str]


@dataclass
class ToolResult:
    success: bool
    output: Any
    error_code: Optional[str] = None
    error_message: Optional[str] = None
    policy_decision: str = "allow"
    deny_reason: Optional[str] = None
    required_scope: Optional[str] = None
    safety_rule: Optional[str] = None
    citations: Optional[List[str]] = None


class ToolPolicyError(RuntimeError):
    def __init__(self, code: str, message: str, deny_reason: str, safety_rule: Optional[str] = None):
        super().__init__(message)
        self.code = code
        self.deny_reason = deny_reason
        self.safety_rule = safety_rule


class ToolRegistry:
    def __init__(self) -> None:
        self._backend_base_url = os.getenv("AGENT_BACKEND_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
        self._backend_token = os.getenv("AGENT_BACKEND_TOKEN", "").strip()
        self._rag_mock_enabled = os.getenv("AGENT_TOOL_RAG_MOCK_ENABLED", "false").lower() in {"1", "true", "yes"}
        self._permission_cache_ttl_ms = self._parse_int_env("AGENT_TOOL_PERMISSION_CACHE_TTL_MS", 10000)
        self._http_allowlist = self._parse_csv_env("AGENT_TOOL_HTTP_ALLOWLIST", "127.0.0.1,localhost")
        self._http_timeout_ms = self._parse_int_env("AGENT_TOOL_HTTP_TIMEOUT_MS", 3000)
        self._http_max_bytes = self._parse_int_env("AGENT_TOOL_HTTP_MAX_BYTES", 20000)
        self._sql_db_path = os.getenv("AGENT_TOOL_SQLITE_PATH", "/tmp/agentops_tool.db")
        self._sql_max_rows = self._parse_int_env("AGENT_TOOL_SQL_MAX_ROWS", 100)
        self._sql_table_allowlist = self._parse_csv_env("AGENT_TOOL_SQL_TABLE_ALLOWLIST", "")
        self._file_write_enabled = os.getenv("AGENT_TOOL_FILE_WRITE_ENABLED", "true").lower() in {"1", "true", "yes"}
        self._file_write_base_dir = pathlib.Path(
            os.getenv("AGENT_TOOL_FILE_WRITE_BASE_DIR", "/tmp/agentops_outputs")
        ).expanduser().resolve()
        self._file_write_max_bytes = self._parse_int_env("AGENT_TOOL_FILE_WRITE_MAX_BYTES", 65536)
        self._file_write_allowlist = self._parse_csv_env(
            "AGENT_TOOL_FILE_WRITE_ALLOW_EXTENSIONS",
            ".txt,.md,.json,.csv",
        )
        self._file_write_default_overwrite = os.getenv("AGENT_TOOL_FILE_WRITE_OVERWRITE_DEFAULT", "false").lower() in {
            "1",
            "true",
            "yes",
        }
        self._role_scopes = self._parse_role_scopes()

        self._specs: Dict[str, ToolSpec] = {}
        self._handlers: Dict[str, Callable[[Dict[str, Any]], ToolResult]] = {}
        self._cached_role_scopes: Dict[str, List[str]] = {}
        self._cached_role_scopes_at_ms: int = 0

        self.register(
            ToolSpec("rag_search", "rag:search", 2500, 0, ["query", "docIds"]),
            self._handle_rag_search,
        )
        self.register(
            ToolSpec("chunk_fetch", "rag:chunk:read", 2500, 0, ["chunkIds"]),
            self._handle_chunk_fetch,
        )
        self.register(
            ToolSpec("http_get", "tool:http_get", self._http_timeout_ms, 0, ["url"]),
            self._handle_http_get,
        )
        self.register(
            ToolSpec("sql_select", "sql:select:readonly", 2000, 0, ["query"]),
            self._handle_sql_select,
        )
        if self._file_write_enabled:
            self.register(
                ToolSpec("file_write", "tool:file_write", 2000, 0, ["path", "content"]),
                self._handle_file_write,
            )

    def register(self, spec: ToolSpec, handler: Callable[[Dict[str, Any]], ToolResult]) -> None:
        self._specs[spec.name] = spec
        self._handlers[spec.name] = handler

    def list_tools(self) -> List[ToolSpec]:
        return list(self._specs.values())

    def list_tools_for_role(self, role: str) -> List[Dict[str, Any]]:
        scopes = set(self.allowed_scopes_for_role(role))
        can_all = "*" in scopes
        visible: List[Dict[str, Any]] = []
        for spec in self.list_tools():
            if not can_all and spec.required_scope not in scopes:
                continue
            visible.append(
                {
                    "name": spec.name,
                    "requiredScope": spec.required_scope,
                    "schemaRequired": list(spec.schema_required),
                    "description": self._tool_description(spec.name),
                }
            )
        return visible

    def allowed_scopes_for_role(self, role: str) -> List[str]:
        normalized = (role or "operator").strip().lower() or "operator"
        merged = dict(self._role_scopes)
        remote = self._load_remote_role_scopes()
        if remote:
            merged.update(remote)
        return merged.get(normalized, [])

    def execute(
        self,
        call_order: int,
        tool_name: str,
        args: Dict[str, Any],
        context: ExecutionContext,
    ) -> tuple[ToolCallTrace, Optional[ToolResult]]:
        started = time.time()
        spec = self._specs.get(tool_name)
        if spec is None:
            trace = ToolCallTrace(
                callOrder=call_order,
                toolName=tool_name,
                requestSummary=self._to_json(args),
                responseSummary="",
                success=False,
                errorCode="TOOL_NOT_REGISTERED",
                errorMessage="Tool is not registered",
                latencyMs=self._latency_ms(started),
                policyDecision="deny",
                denyReason="tool_not_registered",
            )
            return trace, None

        missing_fields = [field for field in spec.schema_required if field not in args]
        if missing_fields:
            trace = ToolCallTrace(
                callOrder=call_order,
                toolName=tool_name,
                requestSummary=self._to_json(args),
                responseSummary="",
                success=False,
                errorCode="TOOL_SCHEMA_INVALID",
                errorMessage=f"Missing required fields: {', '.join(missing_fields)}",
                latencyMs=self._latency_ms(started),
                policyDecision="deny",
                denyReason="schema_invalid",
                requiredScope=spec.required_scope,
            )
            return trace, None

        if not self._has_scope(context.role, spec.required_scope):
            trace = ToolCallTrace(
                callOrder=call_order,
                toolName=tool_name,
                requestSummary=self._to_json(args),
                responseSummary="",
                success=False,
                errorCode="TOOL_SCOPE_DENIED",
                errorMessage=f"Role '{context.role}' lacks scope '{spec.required_scope}'",
                latencyMs=self._latency_ms(started),
                policyDecision="deny",
                denyReason="missing_scope",
                requiredScope=spec.required_scope,
            )
            return trace, None

        try:
            result = self._execute_with_retry(spec, args, context)
        except ToolPolicyError as exc:
            trace = ToolCallTrace(
                callOrder=call_order,
                toolName=tool_name,
                requestSummary=self._to_json(args),
                responseSummary="",
                success=False,
                errorCode=exc.code,
                errorMessage=str(exc),
                latencyMs=self._latency_ms(started),
                policyDecision="deny",
                denyReason=exc.deny_reason,
                requiredScope=spec.required_scope,
                safetyRule=exc.safety_rule,
            )
            return trace, None
        except Exception as exc:
            trace = ToolCallTrace(
                callOrder=call_order,
                toolName=tool_name,
                requestSummary=self._to_json(args),
                responseSummary="",
                success=False,
                errorCode="TOOL_EXECUTION_FAILED",
                errorMessage=str(exc),
                latencyMs=self._latency_ms(started),
                policyDecision="allow",
                requiredScope=spec.required_scope,
            )
            return trace, None

        trace = ToolCallTrace(
            callOrder=call_order,
            toolName=tool_name,
            requestSummary=self._to_json(args),
            responseSummary=self._to_json(result.output),
            success=result.success,
            errorCode=result.error_code,
            errorMessage=result.error_message,
            latencyMs=self._latency_ms(started),
            citations=result.citations or [],
            policyDecision=result.policy_decision,
            denyReason=result.deny_reason,
            requiredScope=result.required_scope or spec.required_scope,
            safetyRule=result.safety_rule,
        )
        return trace, result

    def _execute_with_retry(self, spec: ToolSpec, args: Dict[str, Any], context: ExecutionContext) -> ToolResult:
        last_error: Optional[Exception] = None
        for _attempt in range(spec.retry_max + 1):
            try:
                return self._handlers[spec.name](args, context)
            except ToolPolicyError:
                raise
            except Exception as exc:
                last_error = exc
        if last_error is not None:
            raise last_error
        raise RuntimeError("unknown tool execution error")

    def _handle_rag_search(self, args: Dict[str, Any], context: ExecutionContext) -> ToolResult:
        if self._rag_mock_enabled:
            doc_ids = list(args.get("docIds") or [])
            citations = [f"{doc_id}-c-1" for doc_id in doc_ids[:3]]
            return ToolResult(
                success=True,
                output={"items": [{"chunkId": c} for c in citations], "citations": citations},
                citations=citations,
                policy_decision="allow",
            )
        payload = {
            "query": str(args.get("query") or "").strip(),
            "docIds": list(args.get("docIds") or []),
            "topK": int(args.get("topK") or 5),
        }
        data = self._http_json("POST", f"{self._backend_base_url}/api/rag/search", payload, 2500, context.token)
        items = data.get("items") or []
        citations = [str(item.get("chunkId")) for item in items if item.get("chunkId")]
        return ToolResult(
            success=True,
            output={"items": items, "citations": citations},
            citations=citations,
            policy_decision="allow",
        )

    def _handle_chunk_fetch(self, args: Dict[str, Any], context: ExecutionContext) -> ToolResult:
        chunk_ids = list(args.get("chunkIds") or [])
        if self._rag_mock_enabled:
            items = [{"chunkId": cid, "content": f"mock content for {cid}"} for cid in chunk_ids[:5]]
            citations = [str(item.get("chunkId")) for item in items if item.get("chunkId")]
            return ToolResult(
                success=True,
                output={"items": items, "citations": citations},
                citations=citations,
                policy_decision="allow",
            )
        details: List[Dict[str, Any]] = []
        for chunk_id in chunk_ids[:5]:
            detail = self._http_json("GET", f"{self._backend_base_url}/api/rag/chunk/{urllib.parse.quote(str(chunk_id))}", None, 2500, context.token)
            details.append(detail)
        citations = [str(item.get("chunkId")) for item in details if item.get("chunkId")]
        return ToolResult(
            success=True,
            output={"items": details, "citations": citations},
            citations=citations,
            policy_decision="allow",
        )

    def _handle_http_get(self, args: Dict[str, Any], _context: ExecutionContext) -> ToolResult:
        raw_url = str(args.get("url") or "").strip()
        parsed = urllib.parse.urlparse(raw_url)
        if parsed.scheme not in {"http", "https"} or not parsed.hostname:
            raise ToolPolicyError("HTTP_URL_INVALID", "Invalid http_get url", "invalid_url", "http_url_validation")

        host = parsed.hostname.lower()
        if self._http_allowlist and host not in self._http_allowlist:
            raise ToolPolicyError("HTTP_HOST_NOT_ALLOWED", f"Host '{host}' is not in allowlist", "host_not_allowed", "http_allowlist")

        self._enforce_no_private_host(host)

        req = urllib.request.Request(raw_url, method="GET")
        try:
            with urllib.request.urlopen(req, timeout=self._http_timeout_ms / 1000.0) as resp:
                status_code = int(resp.getcode())
                body_bytes = resp.read(self._http_max_bytes + 1)
                if len(body_bytes) > self._http_max_bytes:
                    raise ToolPolicyError(
                        "HTTP_RESPONSE_TOO_LARGE",
                        f"Response exceeds max bytes {self._http_max_bytes}",
                        "response_too_large",
                        "http_response_limit",
                    )
                text = body_bytes.decode("utf-8", errors="replace")
                return ToolResult(
                    success=True,
                    output={
                        "statusCode": status_code,
                        "contentType": resp.headers.get("Content-Type"),
                        "body": text,
                        "host": host,
                    },
                    policy_decision="allow",
                )
        except urllib.error.URLError as exc:
            raise RuntimeError(f"http_get failed: {exc}") from exc

    def _handle_sql_select(self, args: Dict[str, Any], _context: ExecutionContext) -> ToolResult:
        query = str(args.get("query") or "").strip()
        if not query:
            raise ToolPolicyError("SQL_BAD_REQUEST", "Query is required", "empty_query", "sql_validation")

        self._enforce_readonly_sql(query)
        normalized = self._normalize_sql_limit(query)
        self._enforce_table_allowlist(normalized)

        conn = sqlite3.connect(self._sql_db_path)
        conn.row_factory = sqlite3.Row
        try:
            cursor = conn.execute(normalized)
            rows = [dict(row) for row in cursor.fetchmany(self._sql_max_rows)]
            columns = [desc[0] for desc in cursor.description] if cursor.description else []
            return ToolResult(
                success=True,
                output={"columns": columns, "rows": rows, "rowCount": len(rows)},
                policy_decision="allow",
            )
        finally:
            conn.close()

    def _handle_file_write(self, args: Dict[str, Any], _context: ExecutionContext) -> ToolResult:
        rel_path = str(args.get("path") or "").strip()
        if not rel_path:
            raise ToolPolicyError("FILE_WRITE_BAD_REQUEST", "path is required", "path_required", "file_write_validation")
        if os.path.isabs(rel_path):
            raise ToolPolicyError("FILE_PATH_OUT_OF_SCOPE", "Absolute path is forbidden", "PATH_OUT_OF_SCOPE", "file_write_path_scope")

        content = args.get("content")
        if content is None:
            raise ToolPolicyError("FILE_WRITE_BAD_REQUEST", "content is required", "content_required", "file_write_validation")
        if not isinstance(content, str):
            content = str(content)
        payload = content.encode("utf-8")
        if len(payload) > self._file_write_max_bytes:
            raise ToolPolicyError(
                "FILE_WRITE_TOO_LARGE",
                f"Write payload exceeds max bytes {self._file_write_max_bytes}",
                "FILE_TOO_LARGE",
                "file_write_size_limit",
            )

        suffix = pathlib.Path(rel_path).suffix.lower()
        if not suffix or (self._file_write_allowlist and suffix not in self._file_write_allowlist):
            raise ToolPolicyError(
                "FILE_EXTENSION_NOT_ALLOWED",
                f"Extension '{suffix or '<none>'}' is not in allowlist",
                "EXTENSION_NOT_ALLOWED",
                "file_write_extension_allowlist",
            )

        normalized_rel = pathlib.Path(rel_path)
        if normalized_rel.is_absolute():
            raise ToolPolicyError("FILE_PATH_OUT_OF_SCOPE", "Absolute path is forbidden", "PATH_OUT_OF_SCOPE", "file_write_path_scope")
        candidate = (self._file_write_base_dir / normalized_rel).resolve(strict=False)
        if not str(candidate).startswith(str(self._file_write_base_dir)):
            raise ToolPolicyError("FILE_PATH_OUT_OF_SCOPE", "Path escapes sandbox baseDir", "PATH_OUT_OF_SCOPE", "file_write_path_scope")

        overwrite = bool(args.get("overwrite")) if "overwrite" in args else self._file_write_default_overwrite
        if candidate.exists() and not overwrite:
            raise ToolPolicyError("FILE_ALREADY_EXISTS", "Target file already exists", "FILE_EXISTS", "file_write_overwrite")

        candidate.parent.mkdir(parents=True, exist_ok=True)
        with open(candidate, "w", encoding="utf-8") as f:
            f.write(content)

        relative_output = str(candidate.relative_to(self._file_write_base_dir))
        return ToolResult(
            success=True,
            output={
                "name": candidate.name,
                "relativePath": relative_output,
                "sizeBytes": len(payload),
                "sourceTool": "file_write",
            },
            policy_decision="allow",
        )

    def _parse_role_scopes(self) -> Dict[str, List[str]]:
        raw = os.getenv("AGENT_TOOL_ROLE_SCOPES", "").strip()
        if not raw:
            return {
                "admin": ["*"],
                "operator": ["rag:search", "rag:chunk:read", "tool:http_get", "sql:select:readonly", "tool:file_write"],
                "viewer": ["rag:search", "rag:chunk:read"],
            }
        try:
            parsed = json.loads(raw)
            result: Dict[str, List[str]] = {}
            for key, value in parsed.items():
                if isinstance(value, list):
                    result[str(key).strip().lower()] = [str(item) for item in value]
            return result
        except json.JSONDecodeError:
            return {
                "admin": ["*"],
                "operator": ["rag:search", "rag:chunk:read", "tool:http_get", "sql:select:readonly", "tool:file_write"],
            }

    def _load_remote_role_scopes(self) -> Dict[str, List[str]]:
        now_ms = int(time.time() * 1000)
        if self._cached_role_scopes and now_ms - self._cached_role_scopes_at_ms < self._permission_cache_ttl_ms:
            return self._cached_role_scopes
        try:
            data = self._http_json("GET", f"{self._backend_base_url}/api/tools/permissions", None, 1200)
            if not isinstance(data, list):
                return {}
            loaded: Dict[str, List[str]] = {}
            for item in data:
                if not isinstance(item, dict):
                    continue
                role = str(item.get("role") or "").strip().lower()
                scopes = item.get("scopes")
                if not role or not isinstance(scopes, list):
                    continue
                loaded[role] = [str(scope) for scope in scopes]
            if loaded:
                self._cached_role_scopes = loaded
                self._cached_role_scopes_at_ms = now_ms
            return loaded
        except Exception:
            return {}

    def _has_scope(self, role: str, scope: str) -> bool:
        scopes = self.allowed_scopes_for_role(role)
        return "*" in scopes or scope in scopes

    def _tool_description(self, name: str) -> str:
        mapping = {
            "rag_search": "Search relevant chunks from indexed documents. Best as first retrieval step.",
            "chunk_fetch": "Fetch full content for chunkIds returned by rag_search.",
            "http_get": "Fetch HTTP(S) URL with allowlist and SSRF protections.",
            "sql_select": "Run readonly SELECT SQL query with table allowlist and row cap.",
            "file_write": "Persist content to sandboxed relative file path.",
        }
        return mapping.get(name, "Tool execution")

    def _http_json(self, method: str, url: str, payload: Optional[Dict[str, Any]], timeout_ms: int, token: Optional[str] = None) -> Dict[str, Any]:
        req_data = None
        req = urllib.request.Request(url, method=method)
        req.add_header("Content-Type", "application/json")

        # 优先使用传递的用户 token，否则使用环境变量中的服务账号 token
        auth_token = token if token else self._backend_token
        if auth_token:
            req.add_header("Authorization", f"Bearer {auth_token}")

        if payload is not None:
            req_data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        # Internal runtime<->backend control-plane requests should never inherit host proxy settings.
        no_proxy_opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with no_proxy_opener.open(req, data=req_data, timeout=timeout_ms / 1000.0) as resp:
                text = resp.read().decode("utf-8")
                return json.loads(text) if text else {}
        except urllib.error.HTTPError as exc:
            # 如果是 401 错误且使用的是用户 token，尝试使用服务账号 token 重试
            if exc.code == 401 and token and self._backend_token and token != self._backend_token:
                # 重试：使用服务账号 token
                return self._http_json(method, url, payload, timeout_ms, None)
            body = exc.read().decode("utf-8", errors="ignore") if exc.fp else ""
            raise RuntimeError(f"HTTP {exc.code}: {body}") from exc
        except urllib.error.URLError as exc:
            raise RuntimeError(f"network error: {exc}") from exc

    def _enforce_no_private_host(self, host: str) -> None:
        try:
            infos = socket.getaddrinfo(host, None)
        except socket.gaierror as exc:
            raise ToolPolicyError("HTTP_DNS_RESOLVE_FAILED", f"Failed to resolve host: {host}", "dns_resolve_failed", "ssrf_dns") from exc

        for info in infos:
            ip_text = info[4][0]
            ip_obj = ipaddress.ip_address(ip_text)
            if ip_obj.is_loopback or ip_obj.is_private or ip_obj.is_link_local or ip_obj.is_multicast:
                raise ToolPolicyError(
                    "HTTP_SSRF_BLOCKED",
                    f"Target IP '{ip_text}' is blocked by SSRF policy",
                    "ssrf_blocked",
                    "ssrf_private_cidr",
                )

    def _enforce_readonly_sql(self, query: str) -> None:
        normalized = query.strip().lower()
        if ";" in normalized.rstrip(";"):
            raise ToolPolicyError("SQL_MULTI_STATEMENT_BLOCKED", "Multiple SQL statements are forbidden", "multi_statement", "sql_readonly")
        if not normalized.startswith("select"):
            raise ToolPolicyError("SQL_READONLY_VIOLATION", "Only SELECT is allowed", "readonly_violation", "sql_readonly")
        forbidden = [" insert ", " update ", " delete ", " drop ", " alter ", " create ", " truncate "]
        padded = f" {normalized} "
        for keyword in forbidden:
            if keyword in padded:
                raise ToolPolicyError("SQL_READONLY_VIOLATION", f"Forbidden keyword: {keyword.strip()}", "readonly_violation", "sql_readonly")

    def _enforce_table_allowlist(self, query: str) -> None:
        if not self._sql_table_allowlist:
            return
        if "*" in self._sql_table_allowlist:
            return
        matches = re.findall(r"\bfrom\s+([a-zA-Z0-9_\.]+)", query, re.IGNORECASE)
        matches += re.findall(r"\bjoin\s+([a-zA-Z0-9_\.]+)", query, re.IGNORECASE)
        if not matches:
            return
        for table in matches:
            normalized = table.split(".")[-1].lower()
            if normalized not in self._sql_table_allowlist:
                raise ToolPolicyError(
                    "SQL_TABLE_NOT_ALLOWED",
                    f"Table '{table}' is not in allowlist",
                    "table_not_allowed",
                    "sql_table_allowlist",
                )

    def _normalize_sql_limit(self, query: str) -> str:
        stripped = query.strip().rstrip(";")
        limit_match = re.search(r"\blimit\s+(\d+)\b", stripped, re.IGNORECASE)
        if limit_match is None:
            return f"{stripped} LIMIT {self._sql_max_rows}"
        requested = int(limit_match.group(1))
        if requested > self._sql_max_rows:
            raise ToolPolicyError(
                "SQL_LIMIT_EXCEEDED",
                f"Requested LIMIT {requested} exceeds max {self._sql_max_rows}",
                "limit_exceeded",
                "sql_row_limit",
            )
        return stripped

    def _to_json(self, value: Any) -> str:
        try:
            return json.dumps(value, ensure_ascii=False)
        except TypeError:
            return str(value)

    def _latency_ms(self, started: float) -> int:
        return int((time.time() - started) * 1000)

    def _parse_int_env(self, key: str, default_value: int) -> int:
        raw = os.getenv(key, str(default_value)).strip()
        try:
            return int(raw)
        except ValueError:
            return default_value

    def _parse_csv_env(self, key: str, default_value: str) -> List[str]:
        raw = os.getenv(key, default_value)
        return [item.strip().lower() for item in raw.split(",") if item.strip()]
