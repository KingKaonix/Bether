#!/usr/bin/env python3
"""Linux-side connection state management for tethering to the Android host."""

from __future__ import annotations

import logging
import threading
import time
from enum import Enum
from typing import Optional

logger = logging.getLogger("tetherapp.linux.connection")


class LinuxConnectionState(Enum):
    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    RECONNECTING = "reconnecting"
    FAILED = "failed"


class LinuxConnectionManager:
    """Manages the full Linux client connection stack:

    SOCKS5 client -> redsocks -> iptables redirect -> system-wide routing.
    """

    def __init__(
        self,
        host: str,
        port: int = 1080,
        username: Optional[str] = None,
        password: Optional[str] = None,
        auto_reconnect: bool = True,
        max_reconnect_attempts: int = 10,
        base_backoff: float = 2.0,
    ):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.auto_reconnect = auto_reconnect
        self.max_reconnect_attempts = max_reconnect_attempts
        self.base_backoff = base_backoff

        # Local imports to keep module import-light
        from socks5_client import Socks5Client
        from redsocks_manager import RedsocksManager
        from iptables_manager import IptablesManager

        self.socks_client = Socks5Client(
            host, port, username, password, timeout=10.0
        )
        self.redsocks = RedsocksManager(
            socks_host=host,
            socks_port=port,
            socks_username=username,
            socks_password=password,
        )
        self.iptables = IptablesManager(local_port=12345)

        self.state = LinuxConnectionState.DISCONNECTED
        self._stop = False
        self._reconnect_attempt = 0
        self._lock = threading.Lock()
        self._thread: Optional[threading.Thread] = None

        self.start_time: Optional[float] = None
        self.last_error: Optional[str] = None

    # ------------------------------------------------------------------ #
    # Connection lifecycle
    # ------------------------------------------------------------------ #
    def connect(self) -> bool:
        with self._lock:
            if self.state == LinuxConnectionState.CONNECTED:
                return True
            self.state = LinuxConnectionState.CONNECTING
            self._reconnect_attempt = 0
            self.last_error = None

        logger.info("Connecting to %s:%d ...", self.host, self.port)

        # 1. SOCKS5 connect
        if not self.socks_client.connect():
            self.last_error = "SOCKS5 connection failed"
            self.state = LinuxConnectionState.FAILED
            logger.error(self.last_error)
            return False

        # 2. Start redsocks
        if not self.redsocks.start():
            self.last_error = "redsocks failed to start"
            self.state = LinuxConnectionState.FAILED
            logger.error(self.last_error)
            return False

        # 3. Install iptables redirect
        if not self.iptables.install_redirect():
            self.last_error = "iptables redirect failed (need root?)"
            self.state = LinuxConnectionState.FAILED
            logger.error(self.last_error)
            return False

        self.start_time = time.time()
        self.state = LinuxConnectionState.CONNECTED
        logger.info("Linux client connected to %s:%d", self.host, self.port)
        return True

    def disconnect(self) -> None:
        with self._lock:
            self._stop = True
            if self.state == LinuxConnectionState.DISCONNECTED:
                return

        logger.info("Disconnecting Linux client ...")
        self.iptables.flush_redirect()
        self.redsocks.stop()
        self.socks_client.close()

        self.start_time = None
        self.state = LinuxConnectionState.DISCONNECTED
        logger.info("Linux client disconnected")

    def reconnect(self) -> None:
        self.disconnect()
        time.sleep(self.base_backoff)
        self.connect()

    def run_forever(self) -> None:
        self._thread = threading.current_thread()
        while not self._stop:
            if self.state != LinuxConnectionState.CONNECTED:
                if self.auto_reconnect and self._reconnect_attempt < self.max_reconnect_attempts:
                    self.state = LinuxConnectionState.RECONNECTING
                    self._reconnect_attempt += 1
                    delay = self.base_backoff * (2 ** (self._reconnect_attempt - 1))
                    logger.info("Reconnect attempt %d/%d in %.1fs",
                                self._reconnect_attempt, self.max_reconnect_attempts, delay)
                    time.sleep(delay)
                    self.connect()
                else:
                    time.sleep(1.0)
            else:
                time.sleep(1.0)

    def stop(self) -> None:
        self.disconnect()

    # ------------------------------------------------------------------ #
    # Status
    # ------------------------------------------------------------------ #
    def get_status(self) -> dict:
        return {
            "state": self.state.value,
            "host": self.host,
            "port": self.port,
            "reconnect_attempt": self._reconnect_attempt,
            "start_time": self.start_time,
            "uptime_seconds": (time.time() - self.start_time) if self.start_time else 0,
            "last_error": self.last_error,
            "redsocks": self.redsocks.status(),
            "iptables": self.iptables.status(),
            "socks_connected": self.socks_client.sock is not None,
        }
