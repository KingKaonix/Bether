#!/usr/bin/env python3
"""Redsocks transparent proxy configuration and process management.

Redsocks redirects TCP traffic from iptables to a SOCKS5 proxy (the Android
host). This module generates redsocks.conf, starts/stops the redsocks
process, and exposes its status.
"""

from __future__ import annotations

import logging
import os
import signal
import subprocess
import tempfile
from typing import Optional

logger = logging.getLogger("tetherapp.linux.redsocks")

DEFAULT_REDSOCKS_CONF = """
base {
    log_debug = off;
    log_info = on;
    log = "stderr";
    daemon = on;
    redirector = iptables;
}

redsocks {
    local_ip = 127.0.0.1;
    local_port = 12345;

    ip = {socks_host};
    port = {socks_port};

    type = socks5;
{login}
}
"""


class RedsocksManager:
    def __init__(
        self,
        socks_host: str = "127.0.0.1",
        socks_port: int = 1080,
        socks_username: Optional[str] = None,
        socks_password: Optional[str] = None,
        local_port: int = 12345,
        redsocks_binary: str = "redsocks",
        config_path: Optional[str] = None,
    ):
        self.socks_host = socks_host
        self.socks_port = socks_port
        self.socks_username = socks_username
        self.socks_password = socks_password
        self.local_port = local_port
        self.redsocks_binary = redsocks_binary
        self.config_path = config_path or os.path.join(
            tempfile.gettempdir(), "tetherapp-redsocks.conf"
        )
        self.process: Optional[subprocess.Popen] = None

    def _render_config(self) -> str:
        login_block = ""
        if self.socks_username and self.socks_password:
            login_block = f"""
    login = "{self.socks_username}";
    password = "{self.socks_password}";
"""
        return DEFAULT_REDSOCKS_CONF.format(
            socks_host=self.socks_host,
            socks_port=self.socks_port,
            login=login_block,
        )

    def write_config(self) -> str:
        config = self._render_config()
        with open(self.config_path, "w") as fh:
            fh.write(config)
        logger.info("Wrote redsocks config to %s", self.config_path)
        return self.config_path

    def is_installed(self) -> bool:
        try:
            subprocess.run(
                [self.redsocks_binary, "-h"],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
                timeout=5,
            )
            return True
        except (FileNotFoundError, subprocess.TimeoutExpired):
            return False

    def start(self) -> bool:
        if self.process is not None and self.process.poll() is None:
            logger.warning("redsocks already running")
            return True

        if not self.is_installed():
            logger.error("redsocks binary not found: %s", self.redsocks_binary)
            return False

        self.write_config()
        try:
            self.process = subprocess.Popen(
                [self.redsocks_binary, "-c", self.config_path],
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
            )
            logger.info("Started redsocks (pid=%d) -> %s:%d",
                        self.process.pid, self.socks_host, self.socks_port)
            return True
        except OSError as exc:
            logger.error("Failed to start redsocks: %s", exc)
            self.process = None
            return False

    def stop(self) -> bool:
        if self.process is None:
            return True
        try:
            self.process.send_signal(signal.SIGTERM)
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
        except ProcessLookupError:
            pass
        self.process = None
        logger.info("Stopped redsocks")
        return True

    def is_running(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def restart(self) -> bool:
        self.stop()
        return self.start()

    def status(self) -> dict:
        return {
            "running": self.is_running(),
            "pid": self.process.pid if self.process else None,
            "config": self.config_path,
            "socks_host": self.socks_host,
            "socks_port": self.socks_port,
            "local_port": self.local_port,
        }
