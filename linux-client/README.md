# Bether — Linux Client

Linux-side client for the Bether tethering solution. Connects to the Android host's SOCKS5 proxy and routes all system traffic through it using a SOCKS5 client, redsocks transparent proxy, and iptables NAT/mangle rules. Includes a cyberpunk-themed GTK3 GUI.

## Features

| Component | File | Description |
|-----------|------|-------------|
| SOCKS5 client | `src/socks5_client.py` | SOCKS5 CONNECT, UDP ASSOCIATE, username/password auth |
| Connection manager | `src/linux_connection_manager.py` | Lifecycle: connect → redsocks → iptables redirect, reconnect with exponential backoff |
| redsocks manager | `src/redsocks_manager.py` | Generates redsocks.conf, starts/stops redsocks process |
| iptables manager | `src/iptables_manager.py` | NAT redirect rules for transparent proxying |
| GTK3 GUI | `ui/gtk_client.py` | Cyberpunk-themed window with connect/disconnect, real-time metrics, system tray |
| Connect script | `scripts/connect.sh` | CLI connect |
| Disconnect script | `scripts/disconnect.sh` | CLI disconnect |
| GUI script | `scripts/gui.sh` | Launch GTK GUI |

## Architecture

```
Linux Client                          Android Host (SOCKS5:1080)
┌─────────────────┐                   ┌──────────────────┐
│  Applications   │                   │  SOCKS5 Proxy    │
│  (browser, etc.)│                   │  TCP + UDP       │
└─────────┬───────┘                   └────────┬─────────┘
          │ TCP traffic                        │
          ▼                                    ▼
┌─────────────────┐                   ┌──────────────────┐
│   iptables      │───────────────────►│  Tethering I/F   │
│  REDIRECT 12345 │    (localhost)    │  USB/Wi-Fi/BT    │
└─────────┬───────┘                   └──────────────────┘
          │
          ▼
┌─────────────────┐
│   redsocks      │───────────────────►
│  127.0.0.1:12345│    (SOCKS5)       │
└─────────┬───────┘
          │ SOCKS5 protocol
          ▼
┌─────────────────┐
│ SOCKS5 Client   │───────────────────►  Android Host:1080
│ 127.0.0.1:random│                   (SOCKS5 CONNECT)
└─────────────────┘
```

## Project Structure

```
linux-client/
├── src/
│   ├── __init__.py
│   ├── linux_connection_manager.py   # ConnectionLifecycle + reconnect
│   ├── socks5_client.py              # SOCKS5 protocol client
│   ├── redsocks_manager.py           # redsocks process mgmt
│   ├── iptables_manager.py           # iptables redirect rules
│   └── connection_manager.py         # (legacy/alias)
├── ui/
│   ├── __init__.py
│   ├── __main__.py                   # GUI entry point
│   └── gtk_client.py                 # Cyberpunk GTK3 window
└── scripts/
    ├── connect.sh                    # CLI connect
    ├── disconnect.sh                 # CLI disconnect
    └── gui.sh                        # Launch GTK GUI
```

## Requirements

- Python 3.10+
- PyGObject (GTK3) — `python3-gi`, `gir1.2-gtk-3.0`
- `redsocks` binary (for transparent proxy)
- `iptables` + root (for system-wide redirect)
- `PyYAML` (tests)

Install on Debian/Ubuntu:
```bash
sudo apt install python3-gi python3-pip redsocks iptables
pip install pyyaml
```

Install on Arch:
```bash
sudo pacman -S python-gobject redsocks iptables
pip install pyyaml
```

## Usage

### GUI (recommended)

```bash
cd linux-client
python -m ui.__main__
```

Then enter the Android host IP and port (default `1080`), click **CONNECT**.

### CLI

```bash
./scripts/connect.sh --host 192.168.43.1 --port 1080
./scripts/disconnect.sh
```

### Python API

```python
from linux_client.src.linux_connection_manager import LinuxConnectionManager

mgr = LinuxConnectionManager(host="192.168.43.1", port=1080)
mgr.connect()
# ... traffic flows ...
mgr.disconnect()
```

## Connection States

| State | Meaning |
|-------|---------|
| DISCONNECTED | Not connected |
| CONNECTING | Handshake in progress |
| CONNECTED | Traffic flowing through proxy |
| RECONNECTING | Auto-reconnect attempt |
| FAILED | Connection failed |

Reconnect uses exponential backoff: `base_backoff * 2^(attempt-1)`, up to `max_reconnect_attempts` (default 10).

## Testing

```bash
# From repo root
pip install pytest pyyaml
python -m pytest tests/ -v

# Integration tests only
python -m pytest tests/integration -v
```

## License

MIT