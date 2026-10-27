#!/usr/bin/env python3
"""Entry point for the Linux tethering client GUI.

Usage:
    python3 -m linux_client.ui.gtk_client --host 192.168.1.50 --port 1080
"""

from __future__ import annotations

import argparse
import logging
import sys

from ..src.linux_connection_manager import LinuxConnectionManager
from .gtk_client import run_gui


def main() -> int:
    parser = argparse.ArgumentParser(description="TetherApp Linux Client GUI")
    parser.add_argument("--host", default="127.0.0.1", help="Android SOCKS5 host")
    parser.add_argument("--port", type=int, default=1080, help="SOCKS5 port")
    parser.add_argument("--user", default=None, help="Proxy username (optional)")
    parser.add_argument("--pass", dest="password", default=None, help="Proxy password (optional)")
    parser.add_argument("--no-reconnect", action="store_true", help="Disable auto-reconnect")
    parser.add_argument("--verbose", action="store_true", help="Verbose logging")
    args = parser.parse_args()

    logging.basicConfig(
        level=logging.DEBUG if args.verbose else logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )

    manager = LinuxConnectionManager(
        host=args.host,
        port=args.port,
        username=args.user,
        password=args.password,
        auto_reconnect=not args.no_reconnect,
    )

    run_gui(manager)
    return 0


if __name__ == "__main__":
    sys.exit(main())
