"""Unit tests for the Linux connection manager (state machine only, no network)."""

import sys
import os
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "linux-client", "src"))

from linux_connection_manager import LinuxConnectionManager, LinuxConnectionState


def test_initial_state_disconnected():
    mgr = LinuxConnectionManager("127.0.0.1", 1080)
    assert mgr.state == LinuxConnectionState.DISCONNECTED
    assert mgr.get_status()["state"] == "disconnected"


def test_connect_without_server_fails():
    mgr = LinuxConnectionManager("127.0.0.1", 19999, auto_reconnect=False)
    ok = mgr.connect()
    assert ok is False
    assert mgr.state == LinuxConnectionState.FAILED
    assert mgr.last_error is not None


def test_status_structure():
    mgr = LinuxConnectionManager("127.0.0.1", 1080)
    status = mgr.get_status()
    assert "state" in status
    assert "host" in status
    assert "port" in status
    assert "reconnect_attempt" in status
    assert "redsocks" in status
    assert "iptables" in status
    assert status["host"] == "127.0.0.1"
    assert status["port"] == 1080
