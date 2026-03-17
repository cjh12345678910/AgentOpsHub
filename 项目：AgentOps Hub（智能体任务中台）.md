# 项目：AgentOps Hub（智能体任务中台）

一个“企业级智能体任务平台”：用户用自然语言提交需求，系统将其编排为多步任务，调用工具（RAG 检索/HTTP/SQL/文件生成等）完成，并提供**审计、回放、可观测性、评估**。

## 系统要解决什么

1. 把“对话式需求”变成**可运行的任务**（有状态、有步骤、有重试）

2. 把 agent 的工具调用做成**可控、可审计**（权限、白名单、输入输出记录、成本记录）

3. 把结果质量做成**可验收**（Verifier + Repair、引用证据、离线评估/回归）

# 你最终交付物（做出来就能演示）

## 用户侧（最少一个页面或 Swagger 也行）

创建任务：输入 prompt + 选择资料（上传文档/已有知识库）

查看任务状态：RUNNING/SUCCEEDED/FAILED

查看结果：Markdown + JSON

查看回放：每一步计划、工具调用、证据引用、校验报告、失败原因

## 系统侧能力

多步 agent 执行：Plan → Tool → Verify → (Repair)* → Done

RAG：上传文档 → 切分 → 向量检索 → 引用 chunk 回传

工具体系：注册、schema 校验、权限控制、allowlist、安全限制

可观测性：traceId、step 耗时、token/cost、重试次数、错误码

评估回归：20~50 条用例离线跑分，防“改 prompt 后退化”

# 技术架构（推荐：Java 平台 + Python agent runtime）

- **Java（平台/后端）**：任务系统、队列、存储、权限、审计、RAG 索引服务、可观测性
- **Python（agent 执行器，自研不靠框架）**：状态机、规划/执行/校验循环、工具选择与调用、回写 trace

通信方式：HTTP（简单）或 gRPC（加分但非必需）
 队列：RabbitMQ/Kafka 任一

# 模块拆分（你可以照这个建仓库）

## Java 服务（Spring Boot）

1. **Auth & RBAC**

- 用户、角色、工具权限（tool scope）
- token（JWT）或 session

2. **Task Service**

- 创建任务、查询状态、取消任务
- 任务步骤 Step 的写入与状态更新
- 任务重试/超时/幂等

3. **Queue Worker（或同服务内消费）**

- 消费任务 → 调用 Python Agent Service 执行
- 控制并发、重试、死信队列

4. **Docs & RAG Service**

- 文档上传（pdf/markdown/txt）
- 切分 chunk（按段落/标题）
- 向量化（你可以先用现成 embedding 接口；向量存储可选 pgvector/milvus/ES，MVP 也可先用简单向量库）
- 检索 API（给 agent 用）：topK chunks + 元信息

5. **Audit & Trace**

- 记录每一步输入输出摘要、工具调用参数、响应摘要、错误码
- 支持任务“回放”（按 step 时间线展示）

## Python Agent Service（自研 runtime）

1. `AgentRunner`

- 接收 task payload
- 执行状态机并回写结果

2. `StateMachine`

- PLAN：生成步骤列表（结构化 JSON）
- ACT：按步骤调用工具（RAG/HTTP/SQL/File）
- VERIFY：检查输出质量与证据
- REPAIR：根据 verifier 反馈进行修复（最多 2 次）
- DONE/FAIL：落库并结束

3. `ToolRegistry`

- 工具定义：`name`, `schema`, `permission`, `handler`
- schema 校验（jsonschema）
- 统一超时、重试、trace 注入

4. `TraceReporter`

- 每一步、每次 tool call 回写 Java（带 traceId、stepId、耗时、token/cost

# 核心工作流（一次任务如何跑）

1. 用户创建任务 `POST /api/tasks`
2. Java 写 task=CREATED，推 MQ
3. Worker 消费 → 更新 task=RUNNING → 调 Python `/agent/run`
4. Python：
   - PLAN：拆 steps
   - 对每个 step：
     - 调 RAG：`/api/rag/search`
     - 调工具：HTTP / SQL / File
     - 收集 evidence（chunk_id 列表）
   - VERIFY：格式、字段、引用覆盖率、风险标注
   - 不通过则 REPAIR（最多两轮）
5. Python 回写 Java：结果 + trace
6. Java 更新 SUCCEEDED/FAILED

# API 设计（够用且好讲）

## Java 对外（用户）

```
POST /api/tasks
```

- body：`{prompt, docIds[], mode, outputFormat}`

```
GET /api/tasks/{taskId}
POST /api/tasks/{taskId}/cancel
GET /api/tasks/{taskId}/result
GET /api/tasks/{taskId}/trace
```

## Java 对内（agent 用）

`POST /api/docs/upload` → docId

```
POST /api/rag/search
```

- `{query, docIds[], topK, filters}`

```
GET /api/rag/chunk/{chunkId}
POST /api/audit/tool-call
POST /api/audit/step
```

## Python Agent Service

```
POST /agent/run
```

- `{taskId, prompt, docIds, userContext, constraints}`

# 数据库表结构（直接可建）

### 1) tasks

- `id` (PK)
- `user_id`
- `status` (CREATED/RUNNING/SUCCEEDED/FAILED/CANCELLED)
- `prompt` (TEXT)
- `doc_ids` (JSON)
- `result_json` (JSON/TEXT)
- `result_md` (LONGTEXT)
- `error_message` (TEXT)
- `created_at, updated_at`

### 2) task_steps

- `id` (PK)
- `task_id` (IDX)
- `seq` (step 顺序)
- `step_type` (PLAN/TOOL/VERIFY/REPAIR)
- `status` (RUNNING/SUCCEEDED/FAILED)
- `input_summary` (TEXT)
- `output_summary` (TEXT)
- `started_at, ended_at`
- `latency_ms`
- `model_name`
- `token_in, token_out, cost_est`

### 3) tool_call_logs（最加分的一张表）

- `id` (PK)
- `task_id` (IDX)
- `step_id` (IDX)
- `tool_name`
- `permission_scope`
- `request_json` (TEXT/JSON，建议存摘要+hash，避免敏感)
- `response_json` (同上)
- `success` (BOOL)
- `error_code, error_message`
- `latency_ms`
- `trace_id`
- `created_at`

### 4) docs / doc_chunks

- `docs`: `doc_id, name, uploader, created_at`
- `doc_chunks`: `chunk_id, doc_id, content, metadata_json, embedding_ref`

### 5) tool_permissions（RBAC）

- `role_id, tool_scope, allow`

# 工具设计（先做 4 个就能非常强）

## Tool 统一接口（概念）

- name：`rag_search` / `http_get` / `sql_select` / `file_write`
- schema：参数 JSON schema
- permission：需要的 scope
- handler：执行函数
- common：timeout、retry、trace、日志、脱敏

## 推荐工具

1. **RagSearchTool（必须）**

- 入参：`query, docIds, topK`
- 出参：`[{chunkId, score, snippet, title}]`

1. **ChunkFetchTool（必须）**

- 入参：`chunkId`
- 出参：`content + source`

1. **HttpTool（可选但很加分）**

- 强制 allowlist 域名
- 禁止内网段（SSRF）
- 限制 method（GET/POST）、限时、限响应大小

1. **SqlReadTool（强烈加分）**

- 只允许 SELECT
- 表/字段白名单
- 强制 limit 上限
- 记录审计（查询摘要，不要落敏感数据）

1. **FileTool（输出落地）**

- 生成 Markdown/JSON 文件，存对象存储或本地，并返回下载链接（或直接存 DB）

# Verifier 设计（决定你“像不像 agent 工程”）

Verifier 不用很玄学，核心是**可规则化的验收**。

**校验维度**

- 结构：输出 JSON 是否符合 schema（字段齐全）
- 引用：必须包含 `citations: [chunkId...]`
- 覆盖：关键需求点是否被覆盖（可以用规则 + LLM judge）
- 风险：不确定的地方必须标 `assumption` 或 `risk`

**Repair 策略**

- 把 verifier 的失败原因结构化：
  - `missing_fields`
  - `no_citations`
  - `format_error`
  - `contradiction`
- 允许最多 2 次修复，超过就 FAILED（避免死循环）

# 安全与合规（后端岗很爱问）

1. **权限**

- RBAC：不同 role 能用不同 tool scope（特别是 SQL/HTTP）

2. **审计**

- tool call 全记录（请求/响应存摘要、hash，敏感脱敏）

3. **资源限制**

- 单任务最大 steps、最大 token/cost、最大工具调用次数
- 并发限制、超时取消

4. **注入防护**

- SQL 只读 + 白名单
- HTTP allowlist + SSRF 防护
- 输出渲染时做 XSS/Markdown 安全处理（如果你有前端）

# 可观测性（你能讲得很工程）

- traceId：taskId + stepId + callId 串起来
- metrics：
  - success_rate、avg/p95 latency、retry_count、repair_rate、tool_fail_rate
- 日志：
  - 关键事件：plan 生成、tool 调用、verify 结果、失败原因

# 离线评估与回归（agent 岗会很喜欢）

做一个 `eval_cases.jsonl`（20~50 条）：

- prompt + docIds + 期望输出结构要求
   跑出来指标：
- task_success_rate
- json_schema_pass_rate
- citation_coverage_rate
- avg/p95 latency
- repair_rate

这块你甚至可以做一个 `/api/eval/run`，把结果写一张表，直接在后台看。

# Demo 场景（推荐你主打这个）

**“PRD → 生成 API 列表 → 校验接口规范 → 输出 Markdown + JSON + 引用证据”**

- 上传 PRD、接口约定文档
- 输入一句话
- 平台展示：计划 steps → 检索证据 → 生成接口表 → verifier 通过/失败原因 → 自动修复 → 最终结果
- 回放页能看到每一步工具调用与 chunk 引用

# 开发顺序（不绕路）

1. Java：tasks、steps、tool_call_logs 三表 + 创建/查询/trace API
2. MQ：任务投递与 worker（并发控制、重试）
3. Python：自研 agent runtime（状态机 + registry + trace 回写）
4. RAG：docs upload + chunk + search API（先简单可用）
5. Verifier + Repair（最多两轮）
6. 补：SqlReadTool/HttpTool 的安全限制 + RBAC
7. eval 回归脚本 + 指标输出