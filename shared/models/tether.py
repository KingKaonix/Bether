"""Shared data models for the tether-app project.

Used by both the Android host (Kotlin/Java) and the Linux client (Python).
Models are expressed as plain dataclasses so they can be serialized to JSON
and shared across language boundaries.
"""

from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict, Any
from enum import Enum
import time


class TetherRole(str, Enum):
    HOST = "host"
    CLIENT = "client"
    UNKNOWN = "unknown"


class ConnectionState(str, Enum):
    DISCONNECTED = "disconnected"
    CONNECTING = "connecting"
    CONNECTED = "connected"
    RECONNECTING = "reconnecting"
    FAILED = "failed"


class TetherMode(str, Enum):
    USB = "usb"
    WIFI = "wifi"
    BLUETOOTH = "bluetooth"


class ProxyProtocol(str, Enum):
    SOCKS5 = "socks5"
    TCP = "tcp"
    UDP = "udp"


class BypassLayer(str, Enum):
    TTL_NORMALIZATION = "ttl_normalization"
    IPV6_BLOCKING = "ipv6_blocking"
    DNS_LEAK_PREVENTION = "dns_leak_prevention"
    OS_UPDATE_DOMAIN_BLOCKING = "os_update_domain_blocking"
    MSS_MTU_CLAMPING = "mss_mtu_clamping"
    TRAFFIC_SHAPING = "traffic_shaping"


@dataclass
class BypassStatus:
    layer: BypassLayer
    enabled: bool
    active: bool
    detail: str = ""


@dataclass
class CarrierBypassConfig:
    ttl_normalization: bool = True
    ttl_value: int = 65
    ipv6_blocking: bool = True
    dns_leak_prevention: bool = True
    os_update_domain_blocking: bool = True
    mss_mtu_clamping: bool = True
    mss_value: int = 1360
    traffic_shaping: bool = False


@dataclass
class ConnectionProfile:
    id: str
    name: str
    role: TetherRole
    mode: Optional[TetherMode] = None
    host_address: Optional[str] = None
    host_port: int = 1080
    username: Optional[str] = None
    password: Optional[str] = None
    bypass_config: CarrierBypassConfig = field(default_factory=CarrierBypassConfig)
    auto_reconnect: bool = True
    reconnect_interval: float = 5.0
    max_reconnect_attempts: int = 10
    created_at: float = field(default_factory=time.time)
    last_connected: Optional[float] = None

    def _mask_sensitive(self, d: Dict[str, Any]) -> Dict[str, Any]:
        """Mask sensitive fields like password in serialized dictionaries.
        
        TODO: Production security hardening: Use OS keyring (Android Keystore 
        / libsecret on Linux) instead of storing plain-text passwords in profiles.
        """
        if "password" in d and d["password"] is not None:
            d["password"] = "***"
        return d

    def to_dict(self, include_secrets: bool = False) -> Dict[str, Any]:
        d = asdict(self)
        d["role"] = self.role.value
        d["mode"] = self.mode.value if self.mode else None
        d["bypass_config"] = asdict(self.bypass_config)
        if not include_secrets:
            self._mask_sensitive(d)
        return d

    @classmethod
    def from_dict(cls, d: Dict[str, Any]) -> "ConnectionProfile":
        return cls(
            id=d["id"],
            name=d["name"],
            role=TetherRole(d["role"]),
            mode=TetherMode(d["mode"]) if d.get("mode") else None,
            host_address=d.get("host_address"),
            host_port=d.get("host_port", 1080),
            username=d.get("username"),
            password=d.get("password"),
            bypass_config=CarrierBypassConfig(**d.get("bypass_config", {})),
            auto_reconnect=d.get("auto_reconnect", True),
            reconnect_interval=d.get("reconnect_interval", 5.0),
            max_reconnect_attempts=d.get("max_reconnect_attempts", 10),
            created_at=d.get("created_at", time.time()),
            last_connected=d.get("last_connected"),
        )


@dataclass
class ConnectionInfo:
    state: ConnectionState = ConnectionState.DISCONNECTED
    role: TetherRole = TetherRole.UNKNOWN
    mode: Optional[TetherMode] = None
    interface: Optional[str] = None
    local_address: Optional[str] = None
    remote_address: Optional[str] = None
    local_port: Optional[int] = None
    remote_port: Optional[int] = None
    bytes_sent: int = 0
    bytes_received: int = 0
    start_time: Optional[float] = None
    last_error: Optional[str] = None
    reconnect_attempt: int = 0
    latency_ms: Optional[float] = None

    def to_dict(self) -> Dict[str, Any]:
        d = asdict(self)
        d["state"] = self.state.value
        d["role"] = self.role.value
        d["mode"] = self.mode.value if self.mode else None
        return d


@dataclass
class ProxyRule:
    id: str
    protocol: ProxyProtocol
    listen_address: str
    listen_port: int
    remote_address: str
    remote_port: int
    enabled: bool = True
    udp_forwarding: bool = False


@dataclass
class BandwidthSample:
    timestamp: float
    bytes_sent: int
    bytes_received: int
    bits_per_second_sent: float = 0.0
    bits_per_second_received: float = 0.0


@dataclass
class TetherInterface:
    name: str
    mode: TetherMode
    available: bool
    active: bool = False
    ip_address: Optional[str] = None
    mac_address: Optional[str] = None
    details: Dict[str, Any] = field(default_factory=dict)
