# Agent Runtime (PLAN-ACT-VERIFY)

轻量 Python runtime service for Change-04。  
提供 `POST /agent/run`，返回 phase trace + result + citations。

## Run

```bash
cd agent_runtime
cp .env.example .env
# 编辑 .env，填入真实 AGENT_LLM_API_KEY
python3 server.py --host 0.0.0.0 --port 18080
```

默认会自动加载当前目录下 `.env`。

也可指定其他配置文件：

```bash
python3 server.py --host 0.0.0.0 --port 18080 --env-file /path/to/runtime.env
```

无外网或无密钥调试时，在 `.env` 设置：

```bash
AGENT_LLM_PROVIDER=mock
```

## Parse behavior & 错误语义

- 默认策略是 **fail-fast**：PLAN 阶段若 `extract/parse/validate` 任一失败，任务直接 `FAILED`。
- 应急开关：`AGENT_PLAN_PARSE_FALLBACK_ENABLED=true` 时，PLAN parse 失败会回退 deterministic plan（仅用于临时保活，不建议长期开启）。
- 诊断截断长度：`AGENT_PLAN_RAW_SNIPPET_MAX` 控制 `rawResponseSnippet` 最大长度，避免过长原文泄漏到 trace/result。

错误码 taxonomy（PLAN parse 相关）：

- `llm_empty_output`：LLM 返回空字符串或空白内容。
- `llm_bad_response`：提取到候选文本后 JSON 解析失败。
- `llm_schema_mismatch`：JSON 可解析但不满足 `plan + planSummary` schema。
- `llm_unavailable / llm_timeout`：provider 网络/HTTP/超时失败，非 parser 失败。

## API

`POST /agent/run`

Request:

```json
{
  "taskId": "task-xxx",
  "prompt": "user prompt",
  "docIds": ["doc-1", "doc-2"],
  "outputFormat": "both"
}
```

Response (simplified):

```json
{
  "status": "SUCCEEDED",
  "phase": "VERIFY",
  "phaseStatus": "SUCCEEDED",
  "resultMd": "...",
  "resultJson": "{\"status\":\"succeeded\"}",
  "citations": ["doc-1-c-1"],
  "steps": []
}
```

## Tool Registry（ACT LLM-driven tools）

当前 ACT 阶段由 LLM 决策每轮工具调用（最多多轮），并通过 `ToolRegistry` 执行。  
已内置工具：

- `rag_search`（internal tool，调用 backend `/api/rag/search`）
- `chunk_fetch`（internal tool，调用 backend `/api/rag/chunk/{chunkId}`）
- `http_get`（host allowlist + SSRF guard + response size limit）
- `sql_select`（readonly SQL + table allowlist + row limit）

关键环境变量（见 `.env.example`）：

- `AGENT_BACKEND_BASE_URL`
- `AGENT_TOOL_ROLE_SCOPES`
- `AGENT_TOOL_HTTP_ALLOWLIST`
- `AGENT_TOOL_SQL_TABLE_ALLOWLIST`
- `AGENT_ACT_MAX_ROUNDS`
- `AGENT_ACT_MAX_TOOL_CALLS`

调试建议：

- 在 prompt 中明确描述意图（例如“需要先检索再抓取 chunk，再输出结果”），便于 ACT 决策更稳定。
- 可通过 trace 查看每轮 ACT 选择的工具与入参。
