#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_PID=""
FRONTEND_PID=""

cleanup() {
  if [[ -n "${FRONTEND_PID}" ]] && kill -0 "${FRONTEND_PID}" 2>/dev/null; then
    kill "${FRONTEND_PID}" || true
  fi
  if [[ -n "${BACKEND_PID}" ]] && kill -0 "${BACKEND_PID}" 2>/dev/null; then
    kill "${BACKEND_PID}" || true
  fi
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
  mvn spring-boot:run
) &
BACKEND_PID=$!

wait_for_url "http://localhost:8080/api/simulation/status" "backend"

echo "[dev] starting frontend on http://localhost:4200"
(
  cd "${ROOT_DIR}/frontend"
  npm start
) &
FRONTEND_PID=$!

wait_for_url "http://localhost:4200" "frontend"

echo "[dev] backend pid=${BACKEND_PID}, frontend pid=${FRONTEND_PID}"
wait
