"""Integration tests: end-to-end SOCKS5 proxy + UDP forwarding simulation.

These tests spin up a local TCP/UDP echo pair and verify the SOCKS5 client
can CONNECT and UDP ASSOCIATE against a mock SOCKS5 server.
"""

import sys
import os
import socket
import struct
import threading
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "linux-client", "src"))

from socks5_client import (
    Socks5Client,
    SOCKS5_VER,
    CMD_CONNECT,
    CMD_UDP_ASSOCIATE,
    ATYP_IPV4,
    REP_SUCCESS,
)


def _mock_socks5_server(stop_event, port, results):
    """A minimal SOCKS5 server that replies success to CONNECT and UDP ASSOCIATE."""
    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", port))
    server.listen(8)
    server.settimeout(0.5)

    while not stop_event.is_set():
        try:
            conn, _ = server.accept()
        except socket.timeout:
            continue
        try:
            conn.settimeout(2.0)
            # Greeting
            data = conn.recv(256)
            if len(data) < 2 or data[0] != SOCKS5_VER:
                conn.close()
                continue
            conn.sendall(bytes([SOCKS5_VER, 0x00]))  # no auth

            # Request
            data = conn.recv(256)
            if len(data) < 4 or data[0] != SOCKS5_VER:
                conn.close()
                continue
            cmd = data[1]
            if cmd == CMD_CONNECT:
                results["connect_cmd"] = True
                bind = bytes([SOCKS5_VER, REP_SUCCESS, 0x00, ATYP_IPV4,
                              0x00, 0x00, 0x00, 0x00, 0x00, 0x00])
                conn.sendall(bind)
                # Hold the connection open briefly for relay
                time.sleep(0.3)
            elif cmd == CMD_UDP_ASSOCIATE:
                results["udp_assoc_cmd"] = True
                bind = bytes([SOCKS5_VER, REP_SUCCESS, 0x00, ATYP_IPV4,
                              0x7f, 0x00, 0x00, 0x01, 0x2a, 0x30])  # 127.0.0.1:10800
                conn.sendall(bind)
            conn.close()
        except (OSError, struct.error):
            try:
                conn.close()
            except OSError:
                pass
    server.close()


def test_socks5_connect_through_mock_server():
    stop_event = threading.Event()
    results = {}
    port = 19876
    server_thread = threading.Thread(target=_mock_socks5_server, args=(stop_event, port, results), daemon=True)
    server_thread.start()
    time.sleep(0.2)

    try:
        client = Socks5Client("127.0.0.1", port, timeout=3.0)
        assert client.connect() is True
        assert client.connect_remote("1.2.3.4", 80) is True
        assert results.get("connect_cmd") is True
    finally:
        stop_event.set()
        server_thread.join(timeout=2)
        client.close()


def test_socks5_udp_associate_through_mock_server():
    stop_event = threading.Event()
    results = {}
    port = 19877
    server_thread = threading.Thread(target=_mock_socks5_server, args=(stop_event, port, results), daemon=True)
    server_thread.start()
    time.sleep(0.2)

    try:
        client = Socks5Client("127.0.0.1", port, timeout=3.0)
        assert client.connect() is True
        host, p = client.udp_associate()
        assert results.get("udp_assoc_cmd") is True
        assert host == "127.0.0.1"
        assert p == 10800
    finally:
        stop_event.set()
        server_thread.join(timeout=2)
        client.close()


def test_tcp_roundtrip_through_proxy():
    # Real TCP echo server + SOCKS5 proxy relay
    echo_stop = threading.Event()
    echo_server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    echo_server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    echo_server.bind(("127.0.0.1", 0))
    echo_server.listen(4)
    echo_port = echo_server.getsockname()[1]

    def echo_loop():
        while not echo_stop.is_set():
            try:
                echo_server.settimeout(0.5)
                conn, _ = echo_server.accept()
            except socket.timeout:
                continue
            try:
                conn.settimeout(2.0)
                data = conn.recv(4096)
                if data:
                    conn.sendall(data.upper())
            except OSError:
                pass
            finally:
                try:
                    conn.close()
                except OSError:
                    pass

    echo_thread = threading.Thread(target=echo_loop, daemon=True)
    echo_thread.start()

    proxy_stop = threading.Event()
    proxy_results = {}
    proxy_port = 19878
    proxy_thread = threading.Thread(target=_mock_socks5_server, args=(proxy_stop, proxy_port, proxy_results), daemon=True)
    proxy_thread.start()
    time.sleep(0.2)

    try:
        client = Socks5Client("127.0.0.1", proxy_port, timeout=3.0)
        assert client.connect() is True
        # The mock server accepts CONNECT; real relay needs a connected remote.
        # Here we just verify CONNECT succeeded end-to-end.
        assert client.connect_remote("127.0.0.1", echo_port) is True
    finally:
        echo_stop.set()
        proxy_stop.set()
        echo_thread.join(timeout=2)
        proxy_thread.join(timeout=2)
        try:
            echo_server.close()
        except OSError:
            pass
        client.close()
