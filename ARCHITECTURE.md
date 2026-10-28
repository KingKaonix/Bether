# Bether — Architecture

## Overview

Bether is a cross-platform tethering system with two tiers:

1. **Android Host** — provides internet via USB/Wi-Fi/Bluetooth tethering, exposes a SOCKS5 proxy (TCP + UDP), optional system-wide VPN tunnel, and a 6-layer carrier bypass engine.
2. **Linux Client** — connects to the Android host's SOCKS5 proxy and redirects all system TCP traffic through it using redsocks + iptables.

Shared Python data models define the contract between tiers (serialized as JSON where needed).

## Data Flow

### Android Host → Internet

```
Client Device (Linux)
    │
    │ USB / Wi-Fi / Bluetooth
    ▼
Android Host ──┬──► USB tethering ──► carrier internet
               ├─► Wi-Fi hotspot ───► carrier internet
               ├─► Bluetooth DUN ───► carrier internet
               │
               ├─► SOCKS5 proxy (1080) ──► client apps
               └─► VpnService tunnel ───► system-wide routing
```

### Linux Client → Android Host → Internet

```
Linux Apps (browser, curl, etc.)
        │ TCP traffic
        ▼
   iptables REDIRECT 127.0.0.1:12345
        │
        ▼
   redsocks (transparent SOCKS5)
        │ SOCKS5 protocol
        ▼
   SOCKS5 client → Android Host:1080
        │
        ▼
   Android Host tethering interface → carrier internet
```

## Component Responsibilities

### Android Host

| Component | Responsibility |
|-----------|---------------|
| `RoleManager` | Detects host vs. client capability (USB host + Wi-Fi AP + Bluetooth ≥ 2 → host) |
| `TetherApplication` | Application singleton holding RoleManager, PerformanceMonitor, OptimizationSettings |
| `HostActivity` | UI: USB/Wi-Fi/BT toggles, proxy switch, bypass switch, status display |
| `ClientActivity` | UI: host/port input, connect/disconnect |
| `OnboardingActivity` | First-launch role selection |
| `Socks5ProxyServer` | SOCKS5 CONNECT + UDP ASSOCIATE, NIO selector-based, port 1080 |
| `VpnTetherService` | `VpnService` for system-wide traffic capture |
| `UsbTetherManager` / `WifiHotspotManager` / `BluetoothDunManager` | Per-interface tethering control |
| `CarrierBypassEngine` | 6-layer bypass: TTL, IPv6, DNS, OS-update domains, MSS/MTU, shaping |
| `ConnectionStateMachine` | Connection lifecycle with listener callbacks |
| `PerformanceMonitor` / `OptimizationSettings` | Bandwidth/latency stats, TCP tuning |
| `NetworkStateReceiver` | Connectivity change broadcast receiver |
| `ConnectionManagerService` | Foreground service stub (dataSync type) |

### Linux Client

| Component | Responsibility |
|-----------|---------------|
| `LinuxConnectionManager` | Full connection lifecycle: SOCKS5 → redsocks → iptables, reconnect with backoff |
| `Socks5Client` | SOCKS5 protocol: greeting, auth, CONNECT, UDP ASSOCIATE, relay loop |
| `RedsocksManager` | redsocks.conf generation, process start/stop/status |
| `IptablesManager` | NAT redirect rules (127.0.0.1:12345 → redsocks) |
| `TetherClientWindow` (GTK) | Cyberpunk GUI: connect/disconnect, real-time metrics, tray icon |
| `TetherClientApplication` | GTK application bootstrap |

### Shared

| Component | Responsibility |
|-----------|---------------|
| `ConnectionProfile` | Persisted connection config + bypass settings |
| `ConnectionInfo` | Live connection state + byte counters |
| `CarrierBypassConfig` | 6-layer bypass configuration defaults |
| `ProxyRule` / `BandwidthSample` / `TetherInterface` | Auxiliary data structures |

## Carrier Bypass — 6 Layers

```
Layer 1: TTL normalization      net.ipv4.ip_default_ttl = 65          (root)
Layer 2: IPv6 blocking          net.ipv6.conf.all.disable_ipv6 = 1    (root)
Layer 3: DNS leak prevention    Force DNS via proxy tunnel            (no root)
Layer 4: OS update domain block /etc/hosts deny-list for update domains (root)
Layer 5: MSS/MTU clamping       iptables TCPMSS --set-mss 1360        (root)
Layer 6: Traffic shaping        Token-bucket placeholder              (root)
```

Without root, layers 1, 2, 4, 5, 6 run in **degraded mode** (logged, not enforced).

## Security Considerations

- SOCKS5 proxy supports optional username/password auth
- No TLS between Linux client and Android host (local network only by design)
- Carrier bypass modifies system network settings (requires root on Android)
- iptables/redsocks require root on Linux
- VPN service requires `BIND_VPN_SERVICE` permission on Android

## CI/CD

| Workflow | Trigger | Jobs |
|----------|---------|------|
| `android-build.yml` | push/PR main | Build debug APK (API 21/28/34), unit tests, lint |
| `linux-build.yml` | push/PR main | flake8, black, pytest (3.10/3.11/3.12), script syntax check |
| `integration-test.yml` | push/PR + nightly 02:00 | Integration tests, full suite, carrier bypass config validation, bandit scan |
| `release.yml` | manual dispatch | Release APK, git tag, GitHub release |

> **Path note:** CI workflows reference `tether-app/` as a path prefix, but the repo root contains `android-app/`, `linux-client/`, `shared/`, `tests/` directly. Update workflow paths before relying on CI.

## License

MIT