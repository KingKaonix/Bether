#!/usr/bin/env bash
# Connection management script for the Linux tethering client.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PYTHON="${PYTHON:-python3}"

usage() {
  echo "Usage: $0 {connect|disconnect|status} [options]"
  echo
  echo "Options:"
  echo "  --host HOST     Android SOCKS5 host (default: 127.0.0.1)"
  echo "  --port PORT     SOCKS5 port (default: 1080)"
  echo "  --name NAME     Connection profile name (default: tether-host)"
  echo "  --user USER     Proxy username (optional)"
  echo "  --pass PASS     Proxy password (optional)"
  echo "  --verbose       Verbose logging"
  exit 1
}

CMD=""
HOST="127.0.0.1"
PORT=1080
NAME="tether-host"
USER=""
PASS=""
VERBOSE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    connect|disconnect|status) CMD="$1"; shift ;;
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --name) NAME="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --pass) PASS="$2"; shift 2 ;;
    --verbose) VERBOSE=1; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

if [[ -z "${CMD}" ]]; then
  usage
fi

ARGS=(--host "${HOST}" --port "${PORT}" --name "${NAME}")
if [[ -n "${USER}" ]]; then ARGS+=(--user "${USER}"); fi
if [[ -n "${PASS}" ]]; then ARGS+=(--pass "${PASS}"); fi
if [[ "${VERBOSE}" -eq 1 ]]; then ARGS+=(--verbose); fi

case "${CMD}" in
  connect)
    echo "Connecting to tether host ${HOST}:${PORT} ..."
    "${PYTHON}" "${CLIENT_DIR}/src/connection_manager.py" "${ARGS[@]}"
    ;;
  disconnect)
    echo "Disconnecting tether client ..."
    pkill -f "connection_manager.py" 2>/dev/null || true
    ;;
  status)
    if pgrep -f "connection_manager.py" >/dev/null 2>&1; then
      echo "Tether client: RUNNING"
    else
      echo "Tether client: STOPPED"
    fi
    ;;
esac
