#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="/home/ubuntu/AgentOpsHub"
COMPOSE_FILE="${PROJECT_ROOT}/docker-compose.dev.yml"
DOCKER_BIN="docker"
POSTGRES_TARGET_IMAGE="${POSTGRES_TARGET_IMAGE:-postgres:16}"
REDIS_TARGET_IMAGE="${REDIS_TARGET_IMAGE:-redis:7}"
POSTGRES_BASE_PORT="${POSTGRES_BASE_PORT:-5432}"
REDIS_BASE_PORT="${REDIS_BASE_PORT:-6379}"

require_cmd() {
  local c="$1"
  if ! command -v "$c" >/dev/null 2>&1; then
    echo "[dev-up] Missing command: $c" >&2
    exit 1
  fi
}

setup_docker_cmd() {
  if $DOCKER_BIN info >/dev/null 2>&1; then
    return 0
  fi

  if command -v sudo >/dev/null 2>&1 && sudo -n $DOCKER_BIN info >/dev/null 2>&1; then
    DOCKER_BIN="sudo -E docker"
    return 0
  fi

  if command -v sudo >/dev/null 2>&1; then
    echo "[dev-up] Docker requires elevated permission, trying sudo..." >&2
    if sudo $DOCKER_BIN info >/dev/null 2>&1; then
      DOCKER_BIN="sudo -E docker"
      return 0
    fi
  fi

  echo "[dev-up] Cannot access Docker daemon. Fix by one of the following:" >&2
  echo "  1) sudo usermod -aG docker \$USER && re-login" >&2
  echo "  2) run this script with sudo" >&2
  return 1
}

pull_with_fallback() {
  local target_image="$1"
  shift
  local candidates=("$@")

  echo "[dev-up] Pulling image for ${target_image} ..."
  for image in "${candidates[@]}"; do
    echo "[dev-up] Try: ${image}"
    if $DOCKER_BIN pull "${image}" >/dev/null 2>&1; then
      if [[ "${image}" != "${target_image}" ]]; then
        $DOCKER_BIN tag "${image}" "${target_image}"
      fi
      echo "[dev-up] Pulled ${image} (usable as ${target_image})"
      return 0
    fi
  done

  echo "[dev-up] Failed to pull image for ${target_image}. Tried candidates:" >&2
  printf '  - %s\n' "${candidates[@]}" >&2
  return 1
}

ensure_images() {
  # 优先尝试官方镜像，其次尝试常见 mirror
  pull_with_fallback "${POSTGRES_TARGET_IMAGE}" \
    "${POSTGRES_TARGET_IMAGE}" \
    "docker.m.daocloud.io/library/postgres:16" \
    "mirror.ccs.tencentyun.com/library/postgres:16"

  pull_with_fallback "${REDIS_TARGET_IMAGE}" \
    "${REDIS_TARGET_IMAGE}" \
    "docker.m.daocloud.io/library/redis:7" \
    "mirror.ccs.tencentyun.com/library/redis:7"
}

is_port_in_use() {
  local port="$1"
  (echo >/dev/tcp/127.0.0.1/"${port}") >/dev/null 2>&1
}

pick_available_port() {
  local start_port="$1"
  local max_port=$((start_port + 50))
  local p="$start_port"
  while [[ "$p" -le "$max_port" ]]; do
    if ! is_port_in_use "$p"; then
      echo "$p"
      return 0
    fi
    p=$((p + 1))
  done
  echo "[dev-up] No available port in range ${start_port}-${max_port}" >&2
  return 1
}

wait_for_postgres() {
  local max_retry=30
  local i=1
  while [[ $i -le $max_retry ]]; do
    if $DOCKER_BIN exec agentops-postgres pg_isready -U agentops -d agentops_hub >/dev/null 2>&1; then
      echo "[dev-up] PostgreSQL is ready"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  echo "[dev-up] PostgreSQL not ready in time" >&2
  return 1
}

wait_for_redis() {
  local max_retry=30
  local i=1
  while [[ $i -le $max_retry ]]; do
    if $DOCKER_BIN exec agentops-redis redis-cli ping 2>/dev/null | grep -q "PONG"; then
      echo "[dev-up] Redis is ready"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  echo "[dev-up] Redis not ready in time" >&2
  return 1
}

main() {
  require_cmd docker
  setup_docker_cmd

  ensure_images

  export POSTGRES_IMAGE="${POSTGRES_TARGET_IMAGE}"
  export REDIS_IMAGE="${REDIS_TARGET_IMAGE}"
  export POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT:-$(pick_available_port "$POSTGRES_BASE_PORT")}"
  export REDIS_HOST_PORT="${REDIS_HOST_PORT:-$(pick_available_port "$REDIS_BASE_PORT")}"

  echo "[dev-up] PostgreSQL host port: ${POSTGRES_HOST_PORT}"
  echo "[dev-up] Redis host port: ${REDIS_HOST_PORT}"

  echo "[dev-up] Starting PostgreSQL + Redis ..."
  # 清理旧容器，避免沿用历史端口映射（如之前绑定了 5432/6379）
  $DOCKER_BIN rm -f agentops-postgres agentops-redis >/dev/null 2>&1 || true
  POSTGRES_HOST_PORT="${POSTGRES_HOST_PORT}" REDIS_HOST_PORT="${REDIS_HOST_PORT}" \
    $DOCKER_BIN compose -f "$COMPOSE_FILE" up -d --force-recreate

  wait_for_postgres
  wait_for_redis

  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${POSTGRES_HOST_PORT}/agentops_hub"
  export SPRING_DATASOURCE_USERNAME="agentops"
  export SPRING_DATASOURCE_PASSWORD="agentops"
  export SPRING_REDIS_HOST="127.0.0.1"
  export SPRING_REDIS_PORT="${REDIS_HOST_PORT}"

  echo "[dev-up] Dependencies ready. Starting backend ..."
  cd "$PROJECT_ROOT"
  local run_log="/tmp/agentops-backend-run.log"
  if ! scripts/mvn-proxy.sh run -f backend/pom.xml spring-boot:run -e 2>&1 | tee "$run_log"; then
    echo "[dev-up] Backend start failed. Root-cause hints:" >&2
    grep -E "Caused by:|ERROR|Exception|SQL State|Message" "$run_log" | tail -n 40 >&2 || true
    echo "[dev-up] Full log: $run_log" >&2
    return 1
  fi
}

main "$@"
