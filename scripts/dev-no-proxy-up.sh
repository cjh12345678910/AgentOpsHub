#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/home/ubuntu/AgentOpsHub"

echo "[dev-no-proxy-up] Disable shell proxy for this session"
unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy
export NO_PROXY="127.0.0.1,localhost,dashscope.aliyuncs.com"
export no_proxy="${NO_PROXY}"

echo "[dev-no-proxy-up] Start dependencies (PostgreSQL + Redis)"
docker compose -f "${PROJECT_ROOT}/docker-compose.dev.yml" up -d

echo "[dev-no-proxy-up] Start agent runtime on :18080"
cd "${PROJECT_ROOT}/agent_runtime"
nohup env \
  NO_PROXY="${NO_PROXY}" \
  no_proxy="${no_proxy}" \
  HTTP_PROXY= \
  HTTPS_PROXY= \
  http_proxy= \
  https_proxy= \
  AGENT_BACKEND_BASE_URL="http://127.0.0.1:8080" \
  AGENT_LLM_TIMEOUT_MS="20000" \
  AGENT_LLM_RETRY_MAX="1" \
  python3 server.py --host 0.0.0.0 --port 18080 \
  > /tmp/agent_runtime.log 2>&1 &
RUNTIME_PID=$!

echo "[dev-no-proxy-up] Start backend on :8080"
cd "${PROJECT_ROOT}/backend"
nohup env \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/agentops_hub" \
  SPRING_DATASOURCE_USERNAME="agentops" \
  SPRING_DATASOURCE_PASSWORD="agentops" \
  SPRING_REDIS_HOST="127.0.0.1" \
  SPRING_REDIS_PORT="6379" \
  APP_AGENT_RUNTIME_ENABLED="true" \
  APP_AGENT_RUNTIME_BASE_URL="http://127.0.0.1:18080" \
  NO_PROXY="${NO_PROXY}" \
  no_proxy="${no_proxy}" \
  HTTP_PROXY= \
  HTTPS_PROXY= \
  http_proxy= \
  https_proxy= \
  APP_AGENT_LLM_TIMEOUT_MS="20000" \
  mvn spring-boot:run \
  > /tmp/agent_backend.log 2>&1 &
BACKEND_PID=$!

echo "[dev-no-proxy-up] runtime pid=${RUNTIME_PID}, backend pid=${BACKEND_PID}"
echo "[dev-no-proxy-up] logs:"
echo "  /tmp/agent_runtime.log"
echo "  /tmp/agent_backend.log"
echo "[dev-no-proxy-up] health check:"
echo "  curl --noproxy '*' -sS http://127.0.0.1:18080/agent/health"
