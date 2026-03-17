#!/usr/bin/env bash
set -euo pipefail

PROXY_HOST="${PROXY_HOST:-127.0.0.1}"
PROXY_PORT="${PROXY_PORT:-20081}"
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-/home/ubuntu/AgentOpsHub/.m2}"
TARGET_URL="${TARGET_URL:-https://repo.maven.apache.org/maven2/}"

check_http_proxy() {
  curl -I --max-time 8 --proxy "http://${PROXY_HOST}:${PROXY_PORT}" "${TARGET_URL}" >/dev/null 2>&1
}

check_socks_proxy() {
  curl -I --max-time 8 --proxy "socks5h://${PROXY_HOST}:${PROXY_PORT}" "${TARGET_URL}" >/dev/null 2>&1
}

detect_proxy_scheme() {
  if check_http_proxy; then
    echo "http"
    return 0
  fi

  if check_socks_proxy; then
    echo "socks5h"
    return 0
  fi

  echo "none"
  return 1
}

print_usage() {
  cat <<USAGE
Usage:
  scripts/mvn-proxy.sh check
  scripts/mvn-proxy.sh run <mvn-args...>

Examples:
  scripts/mvn-proxy.sh check
  scripts/mvn-proxy.sh run -f backend/pom.xml -U test
USAGE
}

run_maven() {
  local scheme="$1"
  shift

  mkdir -p "${MAVEN_REPO_LOCAL}"
  unset http_proxy https_proxy HTTP_PROXY HTTPS_PROXY ALL_PROXY all_proxy

  if [[ "${scheme}" == "http" ]]; then
    export http_proxy="http://${PROXY_HOST}:${PROXY_PORT}"
    export https_proxy="http://${PROXY_HOST}:${PROXY_PORT}"
    export HTTP_PROXY="${http_proxy}"
    export HTTPS_PROXY="${https_proxy}"
    echo "[mvn-proxy] Using HTTP proxy ${http_proxy}"
  elif [[ "${scheme}" == "socks5h" ]]; then
    export ALL_PROXY="socks5h://${PROXY_HOST}:${PROXY_PORT}"
    export all_proxy="${ALL_PROXY}"
    export MAVEN_OPTS="${MAVEN_OPTS:-} -DsocksProxyHost=${PROXY_HOST} -DsocksProxyPort=${PROXY_PORT}"
    echo "[mvn-proxy] Using SOCKS5 proxy ${ALL_PROXY}"
    echo "[mvn-proxy] MAVEN_OPTS includes socksProxyHost/socksProxyPort"
  else
    echo "[mvn-proxy] No usable proxy detected on ${PROXY_HOST}:${PROXY_PORT}" >&2
    echo "[mvn-proxy] Please start VPN/proxy process first." >&2
    return 2
  fi

  mvn -Dmaven.repo.local="${MAVEN_REPO_LOCAL}" "$@"
}

main() {
  if [[ $# -lt 1 ]]; then
    print_usage
    exit 1
  fi

  local cmd="$1"
  shift || true

  case "${cmd}" in
    check)
      local scheme
      if scheme="$(detect_proxy_scheme)"; then
        echo "[mvn-proxy] Proxy detected: ${scheme}://${PROXY_HOST}:${PROXY_PORT}"
      else
        echo "[mvn-proxy] Proxy detection failed for ${PROXY_HOST}:${PROXY_PORT}" >&2
        exit 2
      fi
      ;;
    run)
      if [[ $# -lt 1 ]]; then
        echo "[mvn-proxy] Missing mvn args" >&2
        print_usage
        exit 1
      fi

      local scheme
      if scheme="$(detect_proxy_scheme)"; then
        run_maven "${scheme}" "$@"
      else
        echo "[mvn-proxy] Cannot run mvn because proxy is not reachable." >&2
        exit 2
      fi
      ;;
    *)
      print_usage
      exit 1
      ;;
  esac
}

main "$@"
