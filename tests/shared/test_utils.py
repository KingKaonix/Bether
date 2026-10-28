"""Unit tests for shared utils."""

import sys
import os
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "..", "shared"))

from utils import (
    is_valid_ipv4,
    is_valid_ipv6,
    is_valid_hostport,
    parse_hostport,
    resolve_hostname,
    clamp,
    human_readable_bytes,
    format_bitrate,
    ensure_dir,
    read_env_bool,
    read_env_int,
    utc_now,
    format_elapsed,
    sanitize_filename,
)


def test_is_valid_ipv4():
    assert is_valid_ipv4("192.168.1.1") is True
    assert is_valid_ipv4("0.0.0.0") is True
    assert is_valid_ipv4("255.255.255.255") is True
    assert is_valid_ipv4("256.1.1.1") is False
    assert is_valid_ipv4("not-an-ip") is False
    assert is_valid_ipv4("") is False


def test_is_valid_ipv6():
    assert is_valid_ipv6("::1") is True
    assert is_valid_ipv6("fe80::1") is True
    assert is_valid_ipv6("2001:db8::1") is True
    assert is_valid_ipv6("not-an-ip") is False


def test_is_valid_hostport():
    assert is_valid_hostport("127.0.0.1:1080") is True
    assert is_valid_hostport("localhost:1080") is True
    assert is_valid_hostport(":1080") is True
    assert is_valid_hostport("127.0.0.1:0") is False
    assert is_valid_hostport("127.0.0.1:99999") is False
    assert is_valid_hostport("nocolon") is False


def test_parse_hostport():
    assert parse_hostport("127.0.0.1:1080") == ("127.0.0.1", 1080)
    assert parse_hostport("localhost") == ("localhost", 1080)
    assert parse_hostport(":9090", default_port=8080) == ("", 9090)


def test_clamp():
    assert clamp(5, 0, 10) == 5
    assert clamp(-1, 0, 10) == 0
    assert clamp(100, 0, 10) == 10


def test_human_readable_bytes():
    assert human_readable_bytes(0) == "0.00 B"
    assert human_readable_bytes(1024) == "1.00 KB"
    assert human_readable_bytes(1024 * 1024) == "1.00 MB"
    assert human_readable_bytes(1024 ** 3) == "1.00 GB"


def test_format_bitrate():
    s = format_bitrate(500)
    assert "bps" in s
    s = format_bitrate(1500)
    assert "Kbps" in s
    s = format_bitrate(1500000)
    assert "Mbps" in s


def test_ensure_dir():
    d = tempfile.mkdtemp()
    sub = os.path.join(d, "subdir", "nested")
    ensure_dir(sub)
    assert os.path.isdir(sub)


def test_read_env_bool(monkeypatch):
    os.environ["TEST_BOOL_TRUE"] = "1"
    os.environ["TEST_BOOL_FALSE"] = "0"
    os.environ["TEST_BOOL_DEFAULT"] = ""
    assert read_env_bool("TEST_BOOL_TRUE", False) is True
    assert read_env_bool("TEST_BOOL_FALSE", True) is False
    assert read_env_bool("MISSING", True) is True
    assert read_env_bool("MISSING", False) is False


def test_read_env_int(monkeypatch):
    os.environ["TEST_INT"] = "42"
    assert read_env_int("TEST_INT", 0) == 42
    assert read_env_int("MISSING_INT", 7) == 7
    assert read_env_int("NOT_AN_INT", 9) == 9


def test_utc_now():
    t = utc_now()
    assert isinstance(t, float)
    assert t > 0


def test_format_elapsed():
    assert format_elapsed(30) == "30s"
    assert format_elapsed(90) == "1m 30s"
    assert format_elapsed(3700) == "1h 1m 40s"


def test_sanitize_filename():
    assert sanitize_filename("foo/bar:baz") == "foo_bar_baz"
    assert sanitize_filename("clean-name.txt") == "clean-name.txt"
