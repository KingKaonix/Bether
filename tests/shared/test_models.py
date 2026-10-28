"""Unit tests for shared data models."""

import sys
import os
import time

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "shared"))

from models.tether import (
    TetherRole,
    ConnectionState,
    TetherMode,
    ProxyProtocol,
    BypassLayer,
    CarrierBypassConfig,
    ConnectionProfile,
    ConnectionInfo,
    ProxyRule,
    BandwidthSample,
    TetherInterface,
)


def test_carrier_bypass_config_defaults():
    cfg = CarrierBypassConfig()
    assert cfg.ttl_value == 65
    assert cfg.ipv6_blocking is True
    assert cfg.dns_leak_prevention is True
    assert cfg.os_update_domain_blocking is True
    assert cfg.mss_mtu_clamping is True
    assert cfg.mss_value == 1360
    assert cfg.traffic_shaping is False


def test_connection_profile_roundtrip():
    cfg = CarrierBypassConfig(ttl_value=64, mss_value=1200)
    profile = ConnectionProfile(
        id="p1",
        name="test-host",
        role=TetherRole.HOST,
        mode=TetherMode.USB,
        host_address="192.168.43.1",
        host_port=1080,
        username="user",
        password="pass",
        bypass_config=cfg,
        auto_reconnect=True,
        reconnect_interval=3.0,
        max_reconnect_attempts=5,
    )
    d = profile.to_dict()
    assert d["id"] == "p1"
    assert d["role"] == "host"
    assert d["mode"] == "usb"
    assert d["host_address"] == "192.168.43.1"
    assert d["host_port"] == 1080
    assert d["bypass_config"]["ttl_value"] == 64
    assert d["bypass_config"]["mss_value"] == 1200

    restored = ConnectionProfile.from_dict(d)
    assert restored.id == "p1"
    assert restored.role == TetherRole.HOST
    assert restored.mode == TetherMode.USB
    assert restored.bypass_config.ttl_value == 64
    assert restored.bypass_config.mss_value == 1200


def test_connection_info_defaults():
    info = ConnectionInfo()
    assert info.state == ConnectionState.DISCONNECTED
    assert info.role == TetherRole.UNKNOWN
    assert info.bytes_sent == 0
    assert info.bytes_received == 0
    assert info.reconnect_attempt == 0


def test_proxy_rule_defaults():
    rule = ProxyRule(
        id="r1",
        protocol=ProxyProtocol.SOCKS5,
        listen_address="0.0.0.0",
        listen_port=1080,
        remote_address="10.0.0.1",
        remote_port=1080,
    )
    assert rule.enabled is True
    assert rule.udp_forwarding is False


def test_bandwidth_sample():
    sample = BandwidthSample(
        timestamp=time.time(),
        bytes_sent=1024,
        bytes_received=2048,
        bits_per_second_sent=8192.0,
        bits_per_second_received=16384.0,
    )
    assert sample.bytes_sent == 1024
    assert sample.bytes_received == 2048


def test_tether_interface():
    iface = TetherInterface(
        name="usb0",
        mode=TetherMode.USB,
        available=True,
        active=False,
        ip_address="192.168.42.1",
    )
    assert iface.name == "usb0"
    assert iface.mode == TetherMode.USB
    assert iface.available is True
    assert iface.active is False


def test_bypass_layer_enum_values():
    expected = [
        "ttl_normalization",
        "ipv6_blocking",
        "dns_leak_prevention",
        "os_update_domain_blocking",
        "mss_mtu_clamping",
        "traffic_shaping",
    ]
    assert [l.value for l in BypassLayer] == expected


def test_tether_role_enum_values():
    assert TetherRole.HOST.value == "host"
    assert TetherRole.CLIENT.value == "client"
    assert TetherRole.UNKNOWN.value == "unknown"


def test_connection_state_enum_values():
    assert ConnectionState.DISCONNECTED.value == "disconnected"
    assert ConnectionState.CONNECTING.value == "connecting"
    assert ConnectionState.CONNECTED.value == "connected"
    assert ConnectionState.RECONNECTING.value == "reconnecting"
    assert ConnectionState.FAILED.value == "failed"
