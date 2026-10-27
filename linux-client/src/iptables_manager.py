#!/usr/bin/env python3
"""iptables NAT/mangle rules for transparent traffic redirection.

Creates rules that route outbound TCP (and optionally DNS) through the local
redsocks listener, which forwards to the Android SOCKS5 host.
"""

from __future__ import annotations

import logging
import subprocess
from typing import List, Optional

logger = logging.getLogger("tetherapp.linux.iptables")

REDIRECT_CHAIN = "TETHERAPP"
NAT_TABLE = "nat"
MANGLE_TABLE = "mangle"


class IptablesManager:
    def __init__(self, local_port: int = 12345, dry_run: bool = False):
        self.local_port = local_port
        self.dry_run = dry_run
        self._installed_rules: List[str] = []

    def _run(self, args: List[str]) -> Optional[str]:
        cmd = ["iptables"] + args
        if self.dry_run:
            logger.debug("[dry-run] iptables %s", " ".join(args))
            return None
        try:
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=10
            )
            if result.returncode != 0:
                logger.warning("iptables %s failed (rc=%d): %s",
                               " ".join(args), result.returncode, result.stderr.strip())
                return None
            return result.stdout.strip()
        except (FileNotFoundError, subprocess.TimeoutExpired) as exc:
            logger.error("iptables execution error: %s", exc)
            return None

    def _table(self, table: str, args: List[str]) -> Optional[str]:
        return self._run(["-t", table] + args)

    def install_redirect(self) -> bool:
        """Install rules redirecting outbound TCP to the redsocks local port."""
        self.flush_redirect()

        rules = [
            # Create dedicated chain
            ["-N", REDIRECT_CHAIN],
            # Do not redirect localhost / local proxy traffic
            ["-A", REDIRECT_CHAIN, "-d", "127.0.0.0/8", "-j", "RETURN"],
            ["-A", REDIRECT_CHAIN, "-d", "::1/128", "-j", "RETURN"],
            # Redirect all other TCP to redsocks local port
            ["-A", REDIRECT_CHAIN, "-p", "tcp", "-j", "REDIRECT",
             "--to-port", str(self.local_port)],
            # Jump into our chain from OUTPUT
            ["-A", "OUTPUT", "-p", "tcp", "-j", REDIRECT_CHAIN],
        ]

        ok = True
        for args in rules:
            if self._table(NAT_TABLE, args) is None:
                ok = False
            self._installed_rules.append(" ".join(args))

        logger.info("Installed iptables redirect rules (local_port=%d)", self.local_port)
        return ok

    def flush_redirect(self) -> bool:
        """Remove our tethering redirect rules."""
        # Remove the OUTPUT jump rule first
        self._table(NAT_TABLE, ["-D", "OUTPUT", "-p", "tcp", "-j", REDIRECT_CHAIN])
        # Flush and delete the chain
        self._table(NAT_TABLE, ["-F", REDIRECT_CHAIN])
        self._table(NAT_TABLE, ["-X", REDIRECT_CHAIN])
        self._installed_rules.clear()
        logger.info("Flushed iptables redirect rules")
        return True

    def install_mss_clamp(self, mss: int = 1360) -> bool:
        """Clamp MSS in the mangle table to avoid fragmentation over the tunnel."""
        rules = [
            ["-A", "POSTROUTING", "-t", "mangle", "-p", "tcp",
             "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS",
             "--clamp-mss-to-pmtu"],
            ["-A", "POSTROUTING", "-t", "mangle", "-p", "tcp",
             "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS",
             "--set-mss", str(mss)],
        ]
        ok = True
        for args in rules:
            if self._run(args) is None:
                ok = False
        if ok:
            logger.info("Installed MSS clamp (%d)", mss)
        return ok

    def flush_mss_clamp(self) -> bool:
        self._run(["-D", "POSTROUTING", "-t", "mangle", "-p", "tcp",
                   "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS",
                   "--clamp-mss-to-pmtu"])
        self._run(["-D", "POSTROUTING", "-t", "mangle", "-p", "tcp",
                   "--tcp-flags", "SYN,RST", "SYN", "-j", "TCPMSS",
                   "--set-mss", "1360"])
        logger.info("Flushed MSS clamp rules")
        return True

    def is_installed(self) -> bool:
        try:
            subprocess.run(["iptables", "--version"],
                           capture_output=True, timeout=5)
            return True
        except (FileNotFoundError, subprocess.TimeoutExpired):
            return False

    def status(self) -> dict:
        return {
            "chain": REDIRECT_CHAIN,
            "local_port": self.local_port,
            "rules_installed": len(self._installed_rules),
        }


class Ip6tablesManager(IptablesManager):
    """Same as IptablesManager but for ip6tables (IPv6 blocking/redirect)."""

    def _run(self, args: List[str]) -> Optional[str]:
        cmd = ["ip6tables"] + args
        if self.dry_run:
            logger.debug("[dry-run] ip6tables %s", " ".join(args))
            return None
        try:
            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=10
            )
            if result.returncode != 0:
                return None
            return result.stdout.strip()
        except (FileNotFoundError, subprocess.TimeoutExpired):
            return None
