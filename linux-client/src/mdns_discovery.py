#!/usr/bin/env python3
"""mDNS service discovery and advertisement for Bether Android host and Linux client."""

from __future__ import annotations

import logging
import socket
import time
from typing import List, Dict, Optional, Any

logger = logging.getLogger("tetherapp.mdns")

try:
    from zeroconf import ServiceBrowser, Zeroconf, ServiceInfo
    ZEROCONF_AVAILABLE = True
except ImportError:
    ZEROCONF_AVAILABLE = False


class MdnsListener:
    def __init__(self):
        self.discovered_hosts: List[Dict[str, Any]] = []

    def remove_service(self, zeroconf: Zeroconf, type_: str, name: str) -> None:
        self.discovered_hosts = [h for h in self.discovered_hosts if h.get("name") != name]

    def add_service(self, zeroconf: Zeroconf, type_: str, name: str) -> None:
        info = zeroconf.get_service_info(type_, name)
        if info:
            addresses = [socket.inet_ntoa(addr) for addr in info.addresses] if hasattr(info, 'addresses') and info.addresses else []
            host = addresses[0] if addresses else (info.server or "")
            port = info.port
            self.discovered_hosts.append({
                "name": name,
                "host": host,
                "port": port,
                "properties": info.properties if hasattr(info, 'properties') else {},
            })
            logger.info("Discovered tether host: %s at %s:%d", name, host, port)

    def update_service(self, zeroconf: Zeroconf, type_: str, name: str) -> None:
        pass


def discover_hosts(timeout: float = 3.0) -> List[Dict[str, Any]]:
    """Browse for _tetherapp._tcp.local. services on the LAN."""
    if not ZEROCONF_AVAILABLE:
        logger.warning("zeroconf library not available; mDNS discovery skipped.")
        return []

    zeroconf = Zeroconf()
    listener = MdnsListener()
    browser = ServiceBrowser(zeroconf, "_tetherapp._tcp.local.", listener)
    time.sleep(timeout)
    zeroconf.close()
    return listener.discovered_hosts


def advertise_host(name: str, port: int, properties: Optional[Dict[str, bytes]] = None) -> Optional[Any]:
    """Advertise Android tether host service via mDNS (_tetherapp._tcp.local.)."""
    if not ZEROCONF_AVAILABLE:
        logger.warning("zeroconf library not available; mDNS advertisement skipped.")
        return None

    hostname = socket.gethostname()
    info = ServiceInfo(
        "_tetherapp._tcp.local.",
        f"{name}._tetherapp._tcp.local.",
        port=port,
        properties=properties or {},
        server=f"{hostname}.local.",
    )
    zeroconf = Zeroconf()
    try:
        zeroconf.register_service(info)
        logger.info("Advertising mDNS service %s on port %d", name, port)
        return zeroconf
    except Exception as exc:
        logger.error("Failed to advertise mDNS service: %s", exc)
        zeroconf.close()
        return None
