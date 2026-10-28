"""Unit tests for the Linux SOCKS5 client."""

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
    REP_COMMAND_NOT_SUPPORTED,
    relay_loop,
)


def test_socks5_constants():
    assert SOCKS5_VER == 0x05
    assert CMD_CONNECT == 0x01
    assert CMD_UDP_ASSOCIATE == 0x03
    assert ATYP_IPV4 == 0x01
    assert REP_SUCCESS == 0x00


def test_pack_address_ipv4():
    client = Socks5Client("127.0.0.1", 1080)
    packed = client._pack_address("1.2.3.4", 5678)
    assert packed[0] == ATYP_IPV4
    assert packed[1:5] == socket.inet_pton(socket.AF_INET, "1.2.3.4")
    port = struct.unpack("!H", packed[5:7])[0]
    assert port == 5678


def test_pack_address_domain():
    client = Socks5Client("example.com", 1080)
    packed = client._pack_address("example.com", 443)
    assert packed[0] == 0x03
    assert packed[1] == len(b"example.com")
    assert packed[2:2 + 11] == b"example.com"
    port = struct.unpack("!H", packed[2 + 11:2 + 13])[0]
    assert port == 443


def test_parse_address_ipv4():
    client = Socks5Client("127.0.0.1", 1080)
    data = bytes([0x01]) + socket.inet_pton(socket.AF_INET, "1.2.3.4") + struct.pack("!H", 80)
    host, port, offset = client._parse_address(data)
    assert host == "1.2.3.4"
    assert port == 80
    assert offset == len(data)


def test_relay_loop_transfers_data():
    pair1, pair2 = socket.socketpair()
    stop_event = threading.Event()

    t1 = threading.Thread(target=relay_loop, args=(pair1, pair2, stop_event), daemon=True)
    t2 = threading.Thread(target=relay_loop, args=(pair2, pair1, stop_event), daemon=True)
    t1.start()
    t2.start()

    msg = b"hello socks5"
    pair1.sendall(msg)
    time.sleep(0.2)
    received = pair2.recv(4096)
    assert received == msg

    stop_event.set()
    time.sleep(0.1)
    pair1.close()
    pair2.close()


def test_socks5_client_connect_refused():
    client = Socks5Client("127.0.0.1", 19999, timeout=2.0)
    assert client.connect() is False
    assert client.sock is None


def test_auth_no_auth_method():
    # No username/password => method 0x00 selected
    client = Socks5Client("127.0.0.1", 1080)
    assert client.username is None
    assert client.password is None


def test_auth_userpass_set():
    client = Socks5Client("127.0.0.1", 1080, username="u", password="p")
    assert client.username == "u"
    assert client.password == "p"
