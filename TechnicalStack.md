# AgentOps Hub Technical Stack（v1）

> 目标：从 Change-1 开始统一技术栈，避免后续大规模返工。  
> 风格：中文解释为主，technical terms 保留 English。

## 1. Backend Core

- Language: `Java 17`
- Framework: `Spring Boot 3`
- Build Tool: `Maven`
- API Style: `RESTful API`
- API Doc: `OpenAPI/Swagger`（后续补齐）

## 2. Database 与 Persistence（统一基线）

- Primary DB: `PostgreSQL`
- Persistence Layer: `MyBatis`
- SQL Migration: `Flyway`
- 说明：后续所有数据访问默认走 `MyBatis Mapper XML/Annotation`，避免与 `JPA/Hibernate` 混用。

## 3. Cache 与 Session/State 加速

- Cache: `Redis`
- 典型用途：
- task status short-term cache（降低热点查询压力）
- idempotency key / request dedup
- rate limit / temporary lock

## 4. Async 与任务编排（后续阶段）

- Message Queue: `RabbitMQ`（默认）
- 备选：`Kafka`（高吞吐场景）
- 用途：task dispatch、retry、DLQ（dead-letter queue）

## 5. Agent Runtime（后续阶段）

- Language: `Python 3.11+`
- Runtime Pattern: 自研 `State Machine`（PLAN -> ACT -> VERIFY -> REPAIR -> DONE/FAIL）
- Service Interface: `HTTP`（MVP），后续可升级 `gRPC`

## 6. RAG 与检索（后续阶段）

- Document Parsing: Python ecosystem（如 `pypdf` / `markdown` parser）
- Embedding Provider: 可配置（OpenAI-compatible）
- Vector Store: `pgvector`（优先，复用 PostgreSQL）
- Retrieval API: Java 服务对 Agent 暴露统一检索接口

## 7. Frontend

- 当前阶段（Change-1 MVP）：`Vanilla HTML/CSS/JavaScript`
- 建议阶段（Change-2+）：`React + TypeScript + Vite`
- 原则：前端必须可独立配置 `API_BASE_URL`，支持 tester machine 与 server machine 分离部署。

## 8. Observability 与审计（后续阶段）

- Metrics: `Prometheus`
- Dashboard: `Grafana`
- Tracing: `OpenTelemetry`
- Logging: `ELK` 或 `Loki`
- Audit Store: PostgreSQL audit tables

## 9. Testing

- Backend Unit/Integration: `JUnit 5` + `Spring Boot Test`
- DB Integration: `Testcontainers`（PostgreSQL + Redis）
- Frontend E2E: `Playwright`

## 10. Deployment

- Container: `Docker`
- Runtime Env: Linux server / VM
- Future: `Kubernetes`（可选）
- Config Strategy: `.env` + Spring profiles + secret manager（后续）

## 11. 当前仓库状态（As-Is）与目标状态（To-Be）

### As-Is（当前已实现）
- Backend: Spring Boot + JPA + H2 + Flyway
- Frontend: Vanilla HTML/CSS/JS
- Cache: 未接入 Redis

### To-Be（你要求的统一栈）
- Backend: Spring Boot + MyBatis + PostgreSQL + Flyway + Redis
- Frontend: 可先保持 Vanilla（不阻塞），后续升级 React/TS

## 12. 迁移原则（从当前实现切换到统一栈）

- 不做一次性大爆炸重写，按 change/task 渐进替换：
1. 先切 DB 到 PostgreSQL（保留 Flyway）。
2. 将 Repository 层从 JPA 切到 MyBatis（按模块逐步迁移）。
3. 引入 Redis 做只读缓存与热点查询加速。
4. 每一步都保持 API contract 不变，确保前端无需同步大改。

## 13. 环境变量建议（基线）

- `SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/agentops_hub`
- `SPRING_DATASOURCE_USERNAME=<user>`
- `SPRING_DATASOURCE_PASSWORD=<password>`
- `SPRING_REDIS_HOST=<host>`
- `SPRING_REDIS_PORT=6379`
- `APP_CORS_ALLOWED_ORIGINS=http://<frontend-machine-ip>:<port>`

