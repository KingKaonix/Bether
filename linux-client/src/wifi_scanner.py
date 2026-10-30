#!/usr/bin/env python3
"""Wi-Fi network scanner and connection manager for Linux client using NetworkManager (nmcli)."""

from __future__ import annotations

import subprocess
import logging
from typing import List, Dict, Any, Optional

logger = logging.getLogger("tetherapp.linux.wifi")

class WifiScanner:
    @staticmethod
    def scan_networks() -> List[Dict[str, Any]]:
        """Scan available Wi-Fi networks using nmcli."""
        networks = []
        try:
            subprocess.run(["nmcli", "device", "wifi", "rescan"], capture_output=True, text=True, timeout=5)
            res = subprocess.run(
                ["nmcli", "-t", "-f", "SSID,BSSID,CHAN,RATE,SIGNAL,BARS,SECURITY", "device", "wifi", "list"],
                capture_output=True, text=True, check=True, timeout=5
            )
            for line in res.stdout.splitlines():
                parts = line.split(":")
                if len(parts) >= 7:
                    ssid = parts[0].replace("\\:", ":")
                    if not ssid:
                        continue
                    networks.append({
                        "ssid": ssid,
                        "bssid": parts[1],
                        "channel": parts[2],
                        "rate": parts[3],
                        "signal": parts[4],
                        "bars": parts[5],
                        "security": parts[6],
                    })
        except Exception as exc:
            logger.error("Failed to scan Wi-Fi networks via nmcli: %s", exc)
        return networks

    @staticmethod
    def find_kaosnet(networks: List[Dict[str, Any]]) -> Optional[Dict[str, Any]]:
        """Find KAOSNET hotspot among scanned networks."""
        for net in networks:
            if net.get("ssid") == "KAOSNET":
                return net
        return None

    @staticmethod
    def connect_wifi(ssid: str, password: str | None = None) -> bool:
        """Connect to a Wi-Fi network using nmcli."""
        try:
            cmd = ["nmcli", "device", "wifi", "connect", ssid]
            if password:
                cmd.extend(["password", password])
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
            if res.returncode == 0:
                logger.info("Successfully connected to Wi-Fi SSID: %s", ssid)
                return True
            else:
                logger.error("Failed to connect to Wi-Fi: %s", res.stderr)
                return False
        except Exception as exc:
            logger.error("Exception connecting to Wi-Fi SSID %s: %s", ssid, exc)
            return False
