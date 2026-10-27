"""Shared utility functions for the tether-app project."""

from __future__ import annotations

import ipaddress
import logging
import os
import re
import socket
import time
from typing import Any, Dict, List, Optional

logger = logging.getLogger("tetherapp")


def setup_logging(level: int = logging.INFO, name: str = "tetherapp") -> logging.Logger:
    """Configure and return a module logger."""
    logging.basicConfig(
        level=level,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    return logging.getLogger(name)


def is_valid_ipv4(address: str) -> bool:
    try:
        ipaddress.IPv4Address(address)
        return True
    except ValueError:
        return False


def is_valid_ipv6(address: str) -> bool:
    try:
        ipaddress.IPv6Address(address)
        return True
    except ValueError:
        return False


def is_valid_hostport(value: str) -> bool:
    """Validate 'host:port' format."""
    if ":" not in value:
        return False
    host, _, port = value.rpartition(":")
    try:
        port_num = int(port)
    except ValueError:
        return False
    if not (1 <= port_num <= 65535):
        return False
    return host == "" or is_valid_ipv4(host) or is_valid_ipv6(host) or bool(host)


def parse_hostport(value: str, default_port: int = 1080) -> tuple[str, int]:
    if ":" not in value:
        return value, default_port
    host, _, port = value.rpartition(":")
    return host, int(port) if port else default_port


def resolve_hostname(hostname: str) -> List[str]:
    """Resolve a hostname to a list of IP addresses."""
    try:
        infos = socket.getaddrinfo(hostname, None, type=socket.SOCK_STREAM)
        addrs = {info[4][0] for info in infos}
        return sorted(addrs)
    except socket.gaierror as exc:
        logger.warning("Failed to resolve %s: %s", hostname, exc)
        return []


def clamp(value: float, minimum: float, maximum: float) -> float:
    return max(minimum, min(maximum, value))


def human_readable_bytes(num_bytes: int) -> str:
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if abs(num_bytes) < 1024.0:
            return f"{num_bytes:.2f} {unit}"
        num_bytes /= 1024.0
    return f"{num_bytes:.2f} PB"


def format_bitrate(bps: float) -> str:
    if bps < 1024:
        return f"{bps:.2f} bps"
    if bps < 1024 ** 2:
        return f"{bps / 1024:.2f} Kbps"
    if bps < 1024 ** 3:
        return f"{bps / 1024 ** 2:.2f} Mbps"
    return f"{bps / 1024 ** 3:.2f} Gbps"


def ensure_dir(path: str) -> str:
    os.makedirs(path, exist_ok=True)
    return path


def read_env_bool(key: str, default: bool = False) -> bool:
    raw = os.environ.get(key, str(default)).strip().lower()
    return raw in ("1", "true", "yes", "on")


def read_env_int(key: str, default: int) -> int:
    try:
        return int(os.environ.get(key, str(default)).strip())
    except (ValueError, TypeError):
        return default


def utc_now() -> float:
    return time.time()


def format_elapsed(seconds: float) -> str:
    seconds = int(seconds)
    hours, remainder = divmod(seconds, 3600)
    minutes, seconds = divmod(remainder, 60)
    if hours:
        return f"{hours}h {minutes}m {seconds}s"
    if minutes:
        return f"{minutes}m {seconds}s"
    return f"{seconds}s"


def sanitize_filename(name: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", name)
