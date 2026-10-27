#!/usr/bin/env bash
# Disconnect helper for the Linux tethering client.
set -euo pipefail

if pgrep -f "connection_manager.py" >/dev/null 2>&1; then
  pkill -f "connection_manager.py" || true
  echo "Tether client disconnected."
else
  echo "Tether client was not running."
fi
