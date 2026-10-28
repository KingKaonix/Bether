# Bether — Shared Data Models

Python dataclasses shared between the Android host (consumed via JSON serialization) and the Linux client. These models define the common data contract for connection profiles, connection state, bypass configuration, and proxy rules.

## Models

### `TetherRole` (Enum)
- `HOST` — device provides internet
- `CLIENT` — device consumes internet from host
- `UNKNOWN` — role not yet determined

### `ConnectionState` (Enum)
- `DISCONNECTED`
- `CONNECTING`
- `CONNECTED`
- `RECONNECTING`
- `FAILED`

### `TetherMode` (Enum)
- `USB`
- `WIFI`
- `BLUETOOTH`

### `ProxyProtocol` (Enum)
- `SOCKS5`
- `TCP`
- `UDP`

### `BypassLayer` (Enum)
- `TTL_NORMALIZATION`
- `IPV6_BLOCKING`
- `DNS_LEAK_PREVENTION`
- `OS_UPDATE_DOMAIN_BLOCKING`
- `MSS_MTU_CLAMPING`
- `TRAFFIC_SHAPING`

### `CarrierBypassConfig` (dataclass)
Default 6-layer bypass configuration:

| Field | Default | Description |
|-------|---------|-------------|
| `ttl_normalization` | `True` | Normalize IP TTL |
| `ttl_value` | `65` | TTL value to set |
| `ipv6_blocking` | `True` | Disable IPv6 |
| `dns_leak_prevention` | `True` | Force DNS through proxy |
| `os_update_domain_blocking` | `True` | Block OS update domains |
| `mss_mtu_clamping` | `True` | Clamp TCP MSS |
| `mss_value` | `1360` | MSS clamp value |
| `traffic_shaping` | `False` | Enable bandwidth shaping |

### `ConnectionProfile` (dataclass)
Saved connection configuration with full serialization (`to_dict` / `from_dict`) for JSON persistence.

Key fields:
- `id`, `name` — profile identifier
- `role` — `TetherRole`
- `mode` — `TetherMode` (optional)
- `host_address`, `host_port` — remote SOCKS5 host (default port 1080)
- `username`, `password` — optional proxy auth
- `bypass_config` — `CarrierBypassConfig`
- `auto_reconnect`, `reconnect_interval`, `max_reconnect_attempts`
- `created_at`, `last_connected` — timestamps

### `ConnectionInfo` (dataclass)
Live connection state with byte counters and timing.

### `ProxyRule` (dataclass)
Port forwarding rule: listen address/port → remote address/port, with protocol and UDP forwarding toggle.

### `BandwidthSample` (dataclass)
Throughput sample: timestamp, bytes sent/received, bits-per-second.

### `TetherInterface` (dataclass)
Network interface descriptor: name, mode, availability, IP/MAC addresses.

## Usage

```python
from shared.models.tether import (
    ConnectionProfile, CarrierBypassConfig,
    TetherRole, ConnectionState, TetherMode
)

# Create a profile
profile = ConnectionProfile(
    id="home-usb",
    name="Home USB",
    role=TetherRole.CLIENT,
    mode=TetherMode.USB,
    host_address="192.168.43.1",
    host_port=1080,
    bypass_config=CarrierBypassConfig(ttl_value=65, mss_value=1360),
)

# Serialize to JSON
data = profile.to_dict()

# Deserialize from JSON
profile2 = ConnectionProfile.from_dict(data)
```

## Project Structure

```
shared/
├── __init__.py
└── models/
    ├── __init__.py
    └── tether.py      # all dataclasses and enums
```

## Testing

```bash
# From repo root
python -m pytest tests/shared/ -v
```

## License

MIT