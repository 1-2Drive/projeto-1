#!/usr/bin/env bash

set -euo pipefail
# Job control so each background job becomes its own process-group leader; this lets
# cleanup terminate `mvn spring-boot:run` together with the JVM it forks.
set -m

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PID=""
FRONTEND_PID=""

stop_group() {
  local pid="$1"
  [[ -z "${pid}" ]] && return 0
  kill -0 "${pid}" 2>/dev/null || return 0

  # Signal the whole process group so forked JVMs are not left behind.
  kill -TERM -- "-${pid}" 2>/dev/null || kill -TERM "${pid}" 2>/dev/null || true

  for _ in {1..20}; do
    kill -0 "${pid}" 2>/dev/null || return 0
    sleep 0.25
  done

  kill -KILL -- "-${pid}" 2>/dev/null || kill -KILL "${pid}" 2>/dev/null || true
}

cleanup() {
  stop_group "${FRONTEND_PID}"
  stop_group "${BACKEND_PID}"
}

wait_for_url() {
  local url="$1"
  local label="$2"
  for _ in {1..60}; do
    if curl --silent --fail "${url}" >/dev/null 2>&1; then
      echo "[dev] ${label} ready"
      return 0
    fi
    sleep 1
  done
  echo "[dev] ${label} did not become ready: ${url}" >&2
  return 1
}

trap cleanup EXIT INT TERM

echo "[dev] starting backend on http://localhost:8080"
(
  cd "${ROOT_DIR}"
  exec mvn spring-boot:run
) &
BACKEND_PID=$!

wait_for_url "http://localhost:8080/api/simulation/status" "backend"

echo "[dev] starting frontend on http://localhost:4200"
(
  cd "${ROOT_DIR}/frontend"
  exec npm start
) &
FRONTEND_PID=$!

wait_for_url "http://localhost:4200" "frontend"

echo "[dev] backend pid=${BACKEND_PID}, frontend pid=${FRONTEND_PID}"
echo "[dev] press Ctrl+C to stop both services"
wait