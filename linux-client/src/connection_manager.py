#!/usr/bin/env python3
"""Linux client connection manager for tethering to Android host."""

from __future__ import annotations

import argparse
import logging
import signal
import sys
import time
from typing import Optional

sys.path.insert(0, "/data/data/com.opencode.launcher.next/files/home/tether-app/shared")
sys.path.insert(0, "/data/data/com.opencode.launcher.next/files/home/tether-app/linux-client/src")

from models import ConnectionProfile, ConnectionState, TetherRole, TetherMode  # noqa: E402
from utils import setup_logging, human_readable_bytes  # noqa: E402
from socks5_client import Socks5Client  # noqa: E402

logger = logging.getLogger("tetherapp.linux.conn")


class ConnectionManager:
    def __init__(self, profile: ConnectionProfile):
        self.profile = profile
        self.state = ConnectionState.DISCONNECTED
        self.client: Optional[Socks5Client] = None
        self._stop = False
        self._reconnect_attempt = 0
        self._thread: Optional[threading.Thread] = None

    def connect(self) -> bool:
        self.state = ConnectionState.CONNECTING
        self.client = Socks5Client(
            self.profile.host_address or "127.0.0.1",
            self.profile.host_port,
            self.profile.username,
            self.profile.password,
        )
        ok = self.client.connect()
        if ok:
            self.state = ConnectionState.CONNECTED
            self._reconnect_attempt = 0
            self.profile.last_connected = time.time()
            logger.info("Connection established (%s)", self.profile.name)
        else:
            self.state = ConnectionState.FAILED
            logger.error("Connection failed (%s)", self.profile.name)
        return ok

    def disconnect(self) -> None:
        self._stop = True
        if self.client:
            self.client.close()
            self.client = None
        self.state = ConnectionState.DISCONNECTED
        logger.info("Disconnected (%s)", self.profile.name)

    def run_forever(self) -> None:
        import threading
        self._thread = threading.current_thread()
        signal.signal(signal.SIGINT, lambda *_: self.disconnect())
        signal.signal(signal.SIGTERM, lambda *_: self.disconnect())
        while not self._stop:
            if self.state != ConnectionState.CONNECTED:
                if self.profile.auto_reconnect and self._reconnect_attempt < self.profile.max_reconnect_attempts:
                    self.state = ConnectionState.RECONNECTING
                    self._reconnect_attempt += 1
                    logger.info("Reconnect attempt %d/%d ...",
                                self._reconnect_attempt, self.profile.max_reconnect_attempts)
                    time.sleep(self.profile.reconnect_interval * self._reconnect_attempt)
                    self.connect()
                else:
                    time.sleep(1.0)
            else:
                time.sleep(1.0)


def main() -> int:
    parser = argparse.ArgumentParser(description="TetherApp Linux client")
    parser.add_argument("--host", required=True, help="Android SOCKS5 host")
    parser.add_argument("--port", type=int, default=1080, help="SOCKS5 port")
    parser.add_argument("--name", default="tether-host", help="Profile name")
    parser.add_argument("--username", default=None)
    parser.add_argument("--password", default=None)
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()

    setup_logging(logging.DEBUG if args.verbose else logging.INFO)

    profile = ConnectionProfile(
        id=args.name,
        name=args.name,
        role=TetherRole.CLIENT,
        mode=TetherMode.WIFI,
        host_address=args.host,
        host_port=args.port,
        username=args.username,
        password=args.password,
    )
    mgr = ConnectionManager(profile)
    try:
        mgr.connect()
        mgr.run_forever()
    except KeyboardInterrupt:
        pass
    finally:
        mgr.disconnect()
    return 0


if __name__ == "__main__":
    import threading
    sys.exit(main())
