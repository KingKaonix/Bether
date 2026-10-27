#!/usr/bin/env bash
# Launch the Linux tethering client GUI.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLIENT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
PYTHON="${PYTHON:-python3}"

usage() {
  echo "Usage: $0 [options]"
  echo
  echo "Options:"
  echo "  --host HOST      Android SOCKS5 host (default: 127.0.0.1)"
  echo "  --port PORT      SOCKS5 port (default: 1080)"
  echo "  --user USER      Proxy username (optional)"
  echo "  --pass PASS      Proxy password (optional)"
  echo "  --no-reconnect   Disable auto-reconnect"
  echo "  --verbose        Verbose logging"
  echo "  -h, --help       Show this help"
  exit 0
}

HOST="127.0.0.1"
PORT=1080
USER=""
PASS=""
NO_RECONNECT=0
VERBOSE=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --host) HOST="$2"; shift 2 ;;
    --port) PORT="$2"; shift 2 ;;
    --user) USER="$2"; shift 2 ;;
    --pass) PASS="$2"; shift 2 ;;
    --no-reconnect) NO_RECONNECT=1; shift ;;
    --verbose) VERBOSE=1; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

ARGS=(--host "${HOST}" --port "${PORT}")
if [[ -n "${USER}" ]]; then ARGS+=(--user "${USER}"); fi
if [[ -n "${PASS}" ]]; then ARGS+=(--pass "${PASS}"); fi
if [[ "${NO_RECONNECT}" -eq 1 ]]; then ARGS+=(--no-reconnect); fi
if [[ "${VERBOSE}" -eq 1 ]]; then ARGS+=(--verbose); fi

exec "${PYTHON}" -m linux_client.ui.gtk_client "${ARGS[@]}"
