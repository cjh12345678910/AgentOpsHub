#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/home/ubuntu/AgentOpsHub"

check_postgres() {
  if ! pg_isready -h localhost -p 5432 -U agentops >/dev/null 2>&1; then
    echo "[dev-up-local] PostgreSQL is not running on port 5432" >&2
    echo "[dev-up-local] Start it with: sudo systemctl start postgresql" >&2
    return 1
  fi
  echo "[dev-up-local] PostgreSQL is running on port 5432"
}

check_redis() {
  if ! redis-cli -h localhost -p 6379 ping >/dev/null 2>&1; then
    echo "[dev-up-local] Redis is not running on port 6379" >&2
    echo "[dev-up-local] Start it with: sudo systemctl start redis-server" >&2
    return 1
  fi
  echo "[dev-up-local] Redis is running on port 6379"
}

check_database() {
  if ! PGPASSWORD=agentops psql -h localhost -p 5432 -U agentops -d agentops_hub -c "SELECT 1" >/dev/null 2>&1; then
    echo "[dev-up-local] Database agentops_hub does not exist or cannot connect" >&2
    echo "[dev-up-local] Check credentials or create database with:" >&2
    echo "[dev-up-local]   sudo -u postgres createdb -O agentops agentops_hub" >&2
    return 1
  fi
  echo "[dev-up-local] Database agentops_hub is accessible"
}

main() {
  echo "[dev-up-local] Using system PostgreSQL and Redis (no Docker)"
  echo "[dev-up-local] Checking system services..."

  check_postgres
  check_redis
  check_database

  local env_file="$PROJECT_ROOT/backend/.env.local"
  if [[ -f "$env_file" ]]; then
    echo "[dev-up-local] Loading env file: $env_file"
    # shellcheck disable=SC1090
    source "$env_file"
  else
    echo "[dev-up-local] Env file not found, skip: $env_file"
  fi

  echo "[dev-up-local] All dependencies ready. Starting backend..."

  export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/agentops_hub"
  export SPRING_DATASOURCE_USERNAME="agentops"
  export SPRING_DATASOURCE_PASSWORD="agentops"
  export SPRING_REDIS_HOST="localhost"
  export SPRING_REDIS_PORT="6379"

  export APP_AGENT_RUNTIME_ENABLED=true 
  export APP_AGENT_RUNTIME_BASE_URL=http://127.0.0.1:18080 
  export APP_AGENT_RUNTIME_READ_TIMEOUT_MS=90000 
  export APP_AGENT_RUNTIME_CONNECT_TIMEOUT_MS=5000 
  export APP_RAG_ALLOW_LEXICAL_FALLBACK=false 

  cd "$PROJECT_ROOT"
  local run_log="/tmp/agentops-backend-local-run.log"

  # 使用 Maven Wrapper 或系统 Maven（不通过代理脚本）
  local mvn_cmd="mvn"
  if [[ -x "./mvnw" ]]; then
    mvn_cmd="./mvnw"
    echo "[dev-up-local] Using Maven Wrapper"
  else
    echo "[dev-up-local] Using system Maven"
  fi

  if ! $mvn_cmd -f backend/pom.xml spring-boot:run -e 2>&1 | tee "$run_log"; then
    echo "[dev-up-local] Backend start failed. Root-cause hints:" >&2
    grep -E "Caused by:|ERROR|Exception|SQL State|Message" "$run_log" | tail -n 40 >&2 || true
    echo "[dev-up-local] Full log: $run_log" >&2
    return 1
  fi
}

main "$@"
