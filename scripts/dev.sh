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

trap cleanup EXIT INT TERM

echo "[dev] starting backend on http://localhost:8080"
(
  cd "${ROOT_DIR}"
  mvn spring-boot:run
) &
BACKEND_PID=$!

echo "[dev] starting frontend on http://localhost:4200"
(
  cd "${ROOT_DIR}/frontend"
  npm start
) &
FRONTEND_PID=$!

echo "[dev] backend pid=${BACKEND_PID}, frontend pid=${FRONTEND_PID}"
wait
