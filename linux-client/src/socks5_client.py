#!/usr/bin/env python3
"""Linux client SOCKS5 proxy core logic.

Connects to the Android tethering host SOCKS5 server and forwards TCP/UDP
traffic. Designed to be imported by the GUI and connection scripts.
"""

from __future__ import annotations

import socket
import threading
import select
import struct
import logging
import time
from typing import Optional, Tuple

logger = logging.getLogger("tetherapp.linux.client")

SOCKS5_VER = 0x05
CMD_CONNECT = 0x01
CMD_BIND = 0x02
CMD_UDP_ASSOCIATE = 0x03
ATYP_IPV4 = 0x01
ATYP_DOMAIN = 0x03
ATYP_IPV6 = 0x04

REP_SUCCESS = 0x00
REP_GENERAL_FAILURE = 0x01
REP_COMMAND_NOT_SUPPORTED = 0x07
REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08


class Socks5Client:
    """Minimal SOCKS5 client with UDP ASSOCIATE support."""

    def __init__(self, host: str, port: int = 1080,
                 username: Optional[str] = None,
                 password: Optional[str] = None,
                 timeout: float = 10.0):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.timeout = timeout
        self.sock: Optional[socket.socket] = None
        self._lock = threading.Lock()

    def connect(self) -> bool:
        try:
            self.sock = socket.create_connection((self.host, self.port), timeout=self.timeout)
            self.sock.settimeout(self.timeout)
            if not self._authenticate():
                self.close()
                return False
            logger.info("Connected to SOCKS5 proxy %s:%d", self.host, self.port)
            return True
        except OSError as exc:
            logger.error("Failed to connect to SOCKS5 proxy: %s", exc)
            self.close()
            return False

    def close(self) -> None:
        with self._lock:
            if self.sock is not None:
                try:
                    self.sock.close()
                except OSError:
                    pass
                self.sock = None

    def _authenticate(self) -> bool:
        assert self.sock is not None
        methods = b"\x02" if (self.username and self.password) else b"\x00"
        self.sock.sendall(struct.pack("BB", SOCKS5_VER, 1) + methods)
        resp = self.sock.recv(2)
        if len(resp) < 2 or resp[0] != SOCKS5_VER:
            return False
        method = resp[1]
        if method == 0xFF:
            return False
        if method == 0x02:
            return self._auth_userpass()
        return True

    def _auth_userpass(self) -> bool:
        assert self.sock is not None
        u = self.username.encode()
        p = self.password.encode()
        req = struct.pack("BBB", 1, len(u), len(u)) + u + struct.pack("B", len(p)) + p
        self.sock.sendall(req)
        resp = self.sock.recv(2)
        return len(resp) == 2 and resp[1] == 0

    def _pack_address(self, host: str, port: int) -> bytes:
        try:
            ip = socket.inet_pton(socket.AF_INET, host)
            return struct.pack("B", ATYP_IPV4) + ip + struct.pack("!H", port)
        except OSError:
            pass
        try:
            ip = socket.inet_pton(socket.AF_INET6, host)
            return struct.pack("B", ATYP_IPV6) + ip + struct.pack("!H", port)
        except OSError:
            pass
        domain = host.encode()
        if len(domain) > 255:
            raise ValueError("Domain name too long")
        return struct.pack("BB", ATYP_DOMAIN, len(domain)) + domain + struct.pack("!H", port)

    def _parse_address(self, data: bytes, offset: int = 0) -> Tuple[str, int, int]:
        atyp = data[offset]
        offset += 1
        if atyp == ATYP_IPV4:
            host = socket.inet_ntop(socket.AF_INET, data[offset:offset + 4])
            offset += 4
        elif atyp == ATYP_IPV6:
            host = socket.inet_ntop(socket.AF_INET6, data[offset:offset + 16])
            offset += 16
        elif atyp == ATYP_DOMAIN:
            length = data[offset]
            offset += 1
            host = data[offset:offset + length].decode()
            offset += length
        else:
            raise ValueError(f"Unsupported address type: {atyp}")
        port = struct.unpack("!H", data[offset:offset + 2])[0]
        offset += 2
        return host, port, offset

    def connect_remote(self, host: str, port: int) -> bool:
        """Issue a SOCKS5 CONNECT command."""
        assert self.sock is not None
        addr = self._pack_address(host, port)
        req = struct.pack("BBB", SOCKS5_VER, CMD_CONNECT, 0x00) + addr
        self.sock.sendall(req)
        resp = self.sock.recv(256)
        if len(resp) < 4 or resp[0] != SOCKS5_VER or resp[1] != REP_SUCCESS:
            logger.error("SOCKS5 CONNECT failed: len=%s ver=%s rep=%s",
                         len(resp), resp[0:1] if resp else None, resp[1:2] if len(resp) > 1 else None)
            return False
        atyp = resp[3]
        bind_addr_bytes = self._read_bind_address_from_bytes(resp, atyp, 4)
        if bind_addr_bytes is None:
            logger.error("SOCKS5 CONNECT: failed to read bind address")
            return False
        _, _, _ = self._parse_address(resp, 3)
        logger.info("SOCKS5 CONNECT established to %s:%d", host, port)
        return True

    def udp_associate(self) -> Tuple[Optional[str], Optional[int]]:
        """Issue a SOCKS5 UDP ASSOCIATE. Returns (relay_host, relay_port)."""
        assert self.sock is not None
        req = struct.pack("BBB", SOCKS5_VER, CMD_UDP_ASSOCIATE, 0x00) + b"\x01\x00\x00\x00\x00\x00"
        self.sock.sendall(req)
        resp = self.sock.recv(256)
        if len(resp) < 4 or resp[0] != SOCKS5_VER or resp[1] != REP_SUCCESS:
            logger.error("SOCKS5 UDP ASSOCIATE failed")
            return None, None
        atyp = resp[3]
        bind_addr_bytes = self._read_bind_address_from_bytes(resp, atyp, 4)
        if bind_addr_bytes is None:
            logger.error("SOCKS5 UDP ASSOCIATE: failed to read bind address")
            return None, None
        host, port, _ = self._parse_address(resp, 3)
        logger.info("SOCKS5 UDP associate relay: %s:%d", host, port)
        return host, port

    def _read_bind_address_from_bytes(self, data: bytes, atyp: int, offset: int) -> Optional[bytes]:
        """Extract the variable-length bind address from an already-received buffer."""
        if atyp == ATYP_IPV4:
            needed = offset + 6
        elif atyp == 0x04:  # ATYP_IPV6
            needed = offset + 18
        elif atyp == 0x03:  # ATYP_DOMAIN
            if len(data) < offset + 1:
                return None
            dlen = data[offset]
            needed = offset + 1 + dlen + 2
        else:
            return None
        if len(data) < needed:
            return None
        return data[offset:needed]


def relay_loop(client_sock: socket.socket, remote_sock: socket.socket,
               stop_event: threading.Event) -> None:
    """Bidirectional relay between two sockets until stop_event is set."""
    sockets = [client_sock, remote_sock]
    while not stop_event.is_set():
        readable, _, exceptional = select.select(sockets, [], sockets, 0.5)
        for s in readable:
            try:
                data = s.recv(65536)
            except OSError:
                return
            if not data:
                return
            target = remote_sock if s is client_sock else client_sock
            try:
                target.sendall(data)
            except OSError:
                return
        for s in exceptional:
            return


def forward_tcp(local_port: int, remote_host: str, remote_port: int,
                client: Socks5Client, stop_event: threading.Event) -> None:
    """Accept local TCP connections and forward them through SOCKS5."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", local_port))
    server.listen(64)
    server.settimeout(0.5)
    logger.info("Local TCP forwarder listening on 127.0.0.1:%d -> %s:%d",
                local_port, remote_host, remote_port)
    while not stop_event.is_set():
        try:
            conn, _ = server.accept()
        except socket.timeout:
            continue
        except OSError:
            break
        conn.settimeout(client.timeout)
        if not client.connect_remote(remote_host, remote_port):
            conn.close()
            continue
        ev = threading.Event()
        t1 = threading.Thread(target=relay_loop, args=(conn, client.sock, ev), daemon=True)
        t2 = threading.Thread(target=relay_loop, args=(client.sock, conn, ev), daemon=True)
        t1.start()
        t2.start()
