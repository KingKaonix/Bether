"""Carrier bypass validation tests for shared config semantics."""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "shared"))

from models.tether import CarrierBypassConfig


def test_all_six_bypass_layers_present():
    cfg = CarrierBypassConfig()
    assert cfg.ttl_normalization is True
    assert cfg.ipv6_blocking is True
    assert cfg.dns_leak_prevention is True
    assert cfg.os_update_domain_blocking is True
    assert cfg.mss_mtu_clamping is True
    # traffic_shaping is optional, defaults False
    assert cfg.traffic_shaping is False


def test_ttl_default_is_65():
    assert CarrierBypassConfig().ttl_value == 65


def test_mss_default_is_1360():
    assert CarrierBypassConfig().mss_value == 1360


def test_config_serialization_roundtrip():
    cfg = CarrierBypassConfig(
        ttl_normalization=True,
        ttl_value=60,
        ipv6_blocking=False,
        dns_leak_prevention=True,
        os_update_domain_blocking=True,
        mss_mtu_clamping=True,
        mss_value=1280,
        traffic_shaping=True,
    )
    d = {
        "ttl_normalization": cfg.ttl_normalization,
        "ttl_value": cfg.ttl_value,
        "ipv6_blocking": cfg.ipv6_blocking,
        "dns_leak_prevention": cfg.dns_leak_prevention,
        "os_update_domain_blocking": cfg.os_update_domain_blocking,
        "mss_mtu_clamping": cfg.mss_mtu_clamping,
        "mss_value": cfg.mss_value,
        "traffic_shaping": cfg.traffic_shaping,
    }
    restored = CarrierBypassConfig(**d)
    assert restored.ttl_value == 60
    assert restored.ipv6_blocking is False
    assert restored.mss_value == 1280
    assert restored.traffic_shaping is True
