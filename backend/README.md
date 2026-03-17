# AgentOps Hub Backend

## Tech Stack（Change-1 统一基线）

- Framework: `Spring Boot 3`
- Persistence: `MyBatis`
- Database: `PostgreSQL`
- Cache: `Redis`
- Migration: `Flyway`

## 运行方式

```bash
cd backend
mvn spring-boot:run
```

默认监听 `0.0.0.0:8080`，可被局域网内其他机器访问。

## 必要环境变量

```bash
export SPRING_DATASOURCE_URL="jdbc:postgresql://<postgres-host>:5432/agentops_hub"
export SPRING_DATASOURCE_USERNAME="agentops"
export SPRING_DATASOURCE_PASSWORD="agentops"
export SPRING_REDIS_HOST="<redis-host>"
export SPRING_REDIS_PORT="6379"
export APP_CORS_ALLOWED_ORIGIN_PATTERNS="http://localhost:5173,http://127.0.0.1:5173,http://*:5173"
```

## 跨机器前端测试（Server 与 Tester 机器分离）

1. 在后端服务器启动 backend（server machine）。
2. 前端 `frontend/config.js` 已配置为自动使用当前访问主机：`<当前主机>:8080`。
3. 在前端测试机启动静态服务并访问页面进行测试。

## Migration 回滚说明

- 正向 migration: `src/main/resources/db/migration/V1__create_tasks_and_steps.sql`
- 回滚脚本: `db/rollback/V1__rollback_tasks_and_steps.sql`

回滚验证建议：
1. 在测试库执行正向 migration。
2. 执行 rollback 脚本。
3. 确认 `tasks` / `task_steps` 表已删除。

## Maven Central 代理自动适配（HTTP/SOCKS）

如果服务器只能通过本地代理访问外网，使用仓库脚本自动探测代理类型并运行 Maven：

```bash
cd /home/ubuntu/AgentOpsHub
scripts/mvn-proxy.sh check
scripts/mvn-proxy.sh run -f backend/pom.xml -U test
```

默认探测 `127.0.0.1:20081`。可用环境变量覆盖：

```bash
PROXY_HOST=127.0.0.1 PROXY_PORT=20081 scripts/mvn-proxy.sh run -f backend/pom.xml dependency:go-offline
```

## 一键启动本地依赖并运行 Backend（推荐）

在仓库根目录执行：

```bash
cd /home/ubuntu/AgentOpsHub
scripts/dev-up.sh
```

该脚本会：
1. 启动 `PostgreSQL` + `Redis`（`docker-compose.dev.yml`）
2. 等待依赖健康检查通过
3. 注入 backend 所需环境变量
4. 使用代理自适配脚本启动 Spring Boot

如果只想启动依赖，不启动后端：

```bash
docker compose -f docker-compose.dev.yml up -d
```

## Docker 拉取超时时的镜像兜底

`dev-up.sh` 已内置 mirror fallback，会按顺序尝试：
- 官方：`postgres:16` / `redis:7`
- `docker.m.daocloud.io/library/*`
- `mirror.ccs.tencentyun.com/library/*`

你也可以手动指定镜像：

```bash
POSTGRES_TARGET_IMAGE=<your-postgres-image> \
REDIS_TARGET_IMAGE=<your-redis-image> \
/scripts/dev-up.sh
```

## 端口冲突自动处理

`dev-up.sh` 会自动检测并选择可用 host port：
- PostgreSQL: 从 `5432` 开始探测
- Redis: 从 `6379` 开始探测

如果默认端口被占用，会自动切到下一个可用端口，并同步注入 backend 环境变量。

## Tool Governance APIs（Change: Tool Registry）

- `GET /api/tools/catalog?status=enabled|disabled`
- `GET /api/tools/permissions`
- `POST /api/tools/permissions`
- `GET /api/tools/permissions/{role}`
- `PUT /api/tools/permissions/{role}`
- `DELETE /api/tools/permissions/{role}`
- `GET /api/tools/audit?policyDecision=allow|deny&from=<ISO8601>&to=<ISO8601>&limit=100`

说明：
- `tool_call_logs` 已扩展字段：`policy_decision/deny_reason/required_scope/safety_rule`
- Flyway migration: `V5__tool_policy_audit_fields.sql`

## Task Lifecycle APIs（Async Dispatch + Cancel）

- `POST /api/tasks`：创建任务并快速返回 `CREATED`（异步 dispatch）
- `POST /api/tasks/{taskId}/cancel`：取消 `CREATED/RUNNING` 任务
- `GET /api/tasks/{taskId}`
- `GET /api/tasks/{taskId}/result`
- `GET /api/tasks/{taskId}/trace`
- `GET /api/tasks/dlq?limit=20`
- `POST /api/tasks/dlq/{taskId}/replay`
- `POST /api/tasks/dlq/{taskId}/discard?reason=...`

相关配置：

```bash
APP_AGENT_DISPATCH_MODE=threadpool   # threadpool | mq
APP_AGENT_DISPATCH_POOL_SIZE=4
APP_AGENT_DISPATCH_QUEUE_CAPACITY=200
```

MQ 模式额外配置（本地依赖即可，不要求 Docker）：

```bash
SPRING_RABBITMQ_HOST=127.0.0.1
SPRING_RABBITMQ_PORT=5672
SPRING_RABBITMQ_USERNAME=guest
SPRING_RABBITMQ_PASSWORD=guest

APP_TASK_QUEUE_MAX_RETRIES=3
APP_TASK_QUEUE_BASE_BACKOFF_MS=5000
APP_TASK_QUEUE_BACKOFF_MULTIPLIER=2
```

说明：
- `APP_AGENT_DISPATCH_MODE=threadpool` 时不会启动 MQ consumer。
- `APP_AGENT_DISPATCH_MODE=mq` 时 `POST /api/tasks` 采用“落库 + 入队”语义，消费失败进入重试与 DLQ 流程。
