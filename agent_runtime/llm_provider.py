import json
import os
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Dict, List, Optional, Tuple

from llm_parse import parse_plan_response

@dataclass
class ModelUsage:
    model: str
    tokenIn: int
    tokenOut: int
    costEst: float
    latencyMs: int


class LLMProviderError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class LLMProvider:
    def generate_json(self, prompt: str, schema_hint: str, context: Dict[str, Any]) -> Tuple[Dict[str, Any], ModelUsage]:
        raise NotImplementedError

    def generate_text(self, prompt: str, context: Dict[str, Any]) -> Tuple[str, ModelUsage]:
        raise NotImplementedError


class MockLLMProvider(LLMProvider):
    def __init__(self, model: str = "mock-llm-v1"):
        self._model = model

    def generate_json(self, prompt: str, schema_hint: str, context: Dict[str, Any]) -> Tuple[Dict[str, Any], ModelUsage]:
        started = time.time()
        cleaned = (prompt or "").strip()
        if "[llm-bad-json]" in cleaned.lower():
            raise LLMProviderError("llm_bad_response", "Mock provider forced bad json")

        steps = [
            {"index": 1, "name": "analyze", "goal": "理解任务目标并抽取关键词"},
            {"index": 2, "name": "retrieve", "goal": "检索相关文档并收集证据"},
            {"index": 3, "name": "verify", "goal": "检查结果结构、证据覆盖率与风险"},
        ]
        response = {
            "plan": steps,
            "planSummary": f"plan for: {cleaned}",
        }
        usage = self._usage(cleaned, json.dumps(response, ensure_ascii=False), started)
        return response, usage

    def generate_text(self, prompt: str, context: Dict[str, Any]) -> Tuple[str, ModelUsage]:
        started = time.time()
        cleaned = (prompt or "").strip()
        lowered = cleaned.lower()
        phase = str((context or {}).get("phase") or "").lower()
        if "[generate-llm-unavailable]" in lowered and phase == "generate":
            raise LLMProviderError("llm_unavailable", "Mock provider unavailable during generate step")
        if "[llm-unavailable]" in lowered:
            raise LLMProviderError("llm_unavailable", "Mock provider unavailable")

        if (context or {}).get("schemaHint") == "plan-schema":
            if "[llm-bad-json]" in cleaned.lower():
                bad = '{"plan":[{"index":1}], "planSummary": "broken"'
                usage = self._usage(cleaned, bad, started)
                return bad, usage
            if "[llm-empty-output]" in cleaned.lower():
                usage = self._usage(cleaned, "", started)
                return "", usage
            response = json.dumps(
                {
                    "plan": [
                        {"index": 1, "name": "analyze", "goal": "理解任务目标"},
                        {"index": 2, "name": "retrieve", "goal": "检索关键证据"},
                        {"index": 3, "name": "verify", "goal": "校验输出质量"},
                    ],
                    "planSummary": f"plan for: {cleaned}",
                },
                ensure_ascii=False,
            )
            usage = self._usage(cleaned, response, started)
            return response, usage

        if phase == "act":
            round_idx = int((context or {}).get("round") or 1)
            doc_ids = list((context or {}).get("docIds") or [])
            citations = list((context or {}).get("citations") or [])
            has_chunk_fetch = bool((context or {}).get("hasChunkFetch"))
            user_prompt = str((context or {}).get("userPrompt") or "").strip()
            lowered_prompt = user_prompt.lower()

            if round_idx == 1:
                if "[use-http]" in lowered_prompt:
                    url = "https://example.com"
                    for token in user_prompt.split():
                        if token.startswith("http://") or token.startswith("https://"):
                            url = token.strip()
                            break
                    response = {
                        "thought": "user explicitly requested http tool",
                        "toolCalls": [{"tool": "http_get", "args": {"url": url}}],
                        "done": True,
                    }
                    text = json.dumps(response, ensure_ascii=False)
                    usage = self._usage(cleaned, text, started)
                    return text, usage
                if "sql:" in lowered_prompt:
                    sql = user_prompt[user_prompt.lower().find("sql:") + 4 :].strip() or "SELECT 1"
                    response = {
                        "thought": "user explicitly requested sql tool",
                        "toolCalls": [{"tool": "sql_select", "args": {"query": sql}}],
                        "done": True,
                    }
                    text = json.dumps(response, ensure_ascii=False)
                    usage = self._usage(cleaned, text, started)
                    return text, usage
                response = {
                    "thought": "retrieve candidate chunks first",
                    "toolCalls": [
                        {
                            "tool": "rag_search",
                            "args": {"query": user_prompt, "docIds": doc_ids, "topK": 5},
                        }
                    ],
                    "done": False,
                }
            elif citations and not has_chunk_fetch:
                response = {
                    "thought": "need fetch chunk details for grounding",
                    "toolCalls": [
                        {
                            "tool": "chunk_fetch",
                            "args": {"chunkIds": citations[:5]},
                        }
                    ],
                    "done": True,
                }
            else:
                response = {
                    "thought": "enough context for generate",
                    "toolCalls": [],
                    "done": True,
                }
            text = json.dumps(response, ensure_ascii=False)
            usage = self._usage(cleaned, text, started)
            return text, usage

        repaired = "修复后输出：补全缺失字段，增加可追溯 citations，并消除格式歧义。"
        usage = self._usage(cleaned, repaired, started)
        return repaired, usage

    def _usage(self, prompt: str, output: str, started: float) -> ModelUsage:
        token_in = max(1, len(prompt) // 4)
        token_out = max(1, len(output) // 4)
        latency = int((time.time() - started) * 1000)
        return ModelUsage(
            model=self._model,
            tokenIn=token_in,
            tokenOut=token_out,
            costEst=round((token_in * 0.000001) + (token_out * 0.000002), 8),
            latencyMs=latency,
        )


class OpenAICompatibleLLMProvider(LLMProvider):
    def __init__(
        self,
        base_url: str,
        api_key: str,
        model: str,
        timeout_ms: int,
        retry_max: int,
    ):
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._timeout_ms = timeout_ms
        self._retry_max = retry_max

    def generate_json(self, prompt: str, schema_hint: str, context: Dict[str, Any]) -> Tuple[Dict[str, Any], ModelUsage]:
        text, usage = self._chat(prompt, True)
        parsed = parse_plan_response(text)
        if not parsed.ok or parsed.plan is None:
            raise LLMProviderError(parsed.errorCode or "llm_bad_response", parsed.errorMessage or "Invalid JSON plan")
        return parsed.plan, usage

    def generate_text(self, prompt: str, context: Dict[str, Any]) -> Tuple[str, ModelUsage]:
        return self._chat(prompt, False)

    def _chat(self, prompt: str, force_json: bool) -> Tuple[str, ModelUsage]:
        body = {
            "model": self._model,
            "messages": [{"role": "user", "content": prompt}],
            "temperature": 0.1,
        }
        if force_json:
            body["response_format"] = {"type": "json_object"}

        payload = json.dumps(body).encode("utf-8")
        path = f"{self._base_url}/chat/completions"

        last_err: Optional[Exception] = None
        for _attempt in range(self._retry_max + 1):
            started = time.time()
            req = urllib.request.Request(path, method="POST", data=payload)
            req.add_header("Content-Type", "application/json")
            req.add_header("Authorization", f"Bearer {self._api_key}")
            try:
                with urllib.request.urlopen(req, timeout=self._timeout_ms / 1000.0) as resp:
                    content = resp.read().decode("utf-8")
                    data = json.loads(content)
                    choice = (((data.get("choices") or [{}])[0]).get("message") or {}).get("content")
                    if isinstance(choice, list):
                        # 兼容部分 OpenAI-compatible provider 返回 content fragments 数组。
                        fragments: List[str] = []
                        for item in choice:
                            if isinstance(item, dict):
                                text_part = item.get("text")
                                if isinstance(text_part, str):
                                    fragments.append(text_part)
                            elif isinstance(item, str):
                                fragments.append(item)
                        choice = "".join(fragments)
                    if not isinstance(choice, str) or not choice.strip():
                        raise LLMProviderError("llm_empty_output", "Empty completion content")
                    usage_raw = data.get("usage") or {}
                    usage = ModelUsage(
                        model=self._model,
                        tokenIn=int(usage_raw.get("prompt_tokens") or 0),
                        tokenOut=int(usage_raw.get("completion_tokens") or 0),
                        costEst=0.0,
                        latencyMs=int((time.time() - started) * 1000),
                    )
                    return choice, usage
            except urllib.error.HTTPError as exc:
                body = exc.read().decode("utf-8", errors="ignore") if exc.fp else ""
                last_err = LLMProviderError("llm_unavailable", f"HTTP {exc.code}: {body}")
                if exc.code < 500:
                    break
            except urllib.error.URLError as exc:
                last_err = LLMProviderError("llm_timeout", f"Network error: {exc}")
            except TimeoutError as exc:
                last_err = LLMProviderError("llm_timeout", f"Timeout: {exc}")
            except json.JSONDecodeError as exc:
                last_err = LLMProviderError("llm_bad_response", f"Invalid json response: {exc}")
                break
        if isinstance(last_err, LLMProviderError):
            raise last_err
        raise LLMProviderError("llm_unavailable", "LLM request failed")


def build_provider_from_env() -> LLMProvider:
    provider = os.getenv("AGENT_LLM_PROVIDER", "openai-compatible").strip().lower()
    model = os.getenv("AGENT_LLM_MODEL", "gpt-4o-mini")

    if provider in {"mock", "local"}:
        return MockLLMProvider(model=f"{provider}-{model}")

    if provider in {"openai", "openai-compatible"}:
        base_url = os.getenv("AGENT_LLM_BASE_URL", "https://api.openai.com/v1")
        api_key = os.getenv("AGENT_LLM_API_KEY", "").strip()
        timeout_ms = int(os.getenv("AGENT_LLM_TIMEOUT_MS", "8000"))
        retry_max = int(os.getenv("AGENT_LLM_RETRY_MAX", "1"))
        if not api_key:
            raise LLMProviderError("llm_unavailable", "AGENT_LLM_API_KEY is required for openai-compatible provider")
        return OpenAICompatibleLLMProvider(
            base_url=base_url,
            api_key=api_key,
            model=model,
            timeout_ms=timeout_ms,
            retry_max=retry_max,
        )

    raise LLMProviderError("llm_unavailable", f"Unsupported provider: {provider}")
