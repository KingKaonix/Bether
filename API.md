# Bether — API Reference

## Android Host (Java)

### `RoleManager`
- `detectRole()` → `DeviceRole` — HOST if ≥2 of USB host/Wi-Fi/Bluetooth available, else CLIENT
- `getDetectedRole()` → `DeviceRole`
- `isHost()` / `isClient()` → boolean
- `isHostCapable()` → boolean
- `setRole(DeviceRole)` — persist preference
- `clearPreferredRole()` — re-detect
- `getRoleSummary()` → String

### `Socks5ProxyServer`
- `Socks5ProxyServer(Context, int listenPort, boolean udpEnabled)`
- `start()` → boolean — binds port, starts accept + UDP threads
- `stop()` → boolean
- `isRunning()` → boolean
- `getListenPort()` → int
- `addListener(ProxyListener)` / `removeListener(ProxyListener)`

**ProxyListener callbacks:**
- `onProxyStarted(int port)`
- `onProxyStopped()`
- `onClientConnected(String address)`
- `onClientDisconnected(String address)`
- `onTcpRelay(String host, int port, long bytes)`
- `onUdpRelay(String host, int port, long bytes)`
- `onError(String message)`

### `CarrierBypassEngine`
- `CarrierBypassEngine(Context)`
- `applyTtlNormalization(int ttl)` → boolean
- `applyIpv6Blocking()` → boolean
- `applyDnsLeakPrevention()` → boolean
- `applyOsUpdateDomainBlocking()` → boolean
- `applyMssMtuClamping(int mss, int mtu)` → boolean
- `applyTrafficShaping()` → boolean
- `disableAll()` → boolean
- `addListener(BypassListener)` / `removeListener(BypassListener)`
- `getTtlState()` / `getIpv6State()` / `getDnsState()` / `getOsUpdateState()` / `getMssState()` / `getShapingState()` → `LayerState`
- `activeLayerCount()` → int (0–6)
- `isRoot()` → boolean

**LayerState:** DISABLED / ACTIVE / DEGRADED / FAILED

### `ConnectionStateMachine`
- `ConnectionStateMachine(int maxAttempts, long backoffMs)`
- `connect()` / `disconnect()`
- `isConnected()` / `isConnecting()` → boolean
- `addListener` / `removeListener` (state change callbacks)
- `shutdown()`

### `UsbTetherManager` / `WifiHotspotManager` / `BluetoothDunManager`
- `startTethering()` / `stopTethering()` / `isTetheringActive()` → boolean
- `discoverTetherDevices()` → List<UsbDevice> (USB only)

### `VpnTetherService`
- Android `VpnService` — binds `BIND_VPN_SERVICE`, handles system-wide tunnel

---

## Linux Client (Python)

### `LinuxConnectionManager`
```python
LinuxConnectionManager(
    host: str,
    port: int = 1080,
    username: Optional[str] = None,
    password: Optional[str] = None,
    auto_reconnect: bool = True,
    max_reconnect_attempts: int = 10,
    base_backoff: float = 2.0,
)
```

**Methods:**
- `connect()` → bool — SOCKS5 connect → redsocks start → iptables redirect
- `disconnect()` — flush iptables, stop redsocks, close SOCKS5
- `reconnect()` — disconnect + backoff + connect
- `run_forever()` — blocking loop with auto-reconnect
- `stop()` — alias for disconnect
- `get_status()` → dict — state, host, port, uptime, reconnect attempts, last error, sub-component status

**States (`LinuxConnectionState`):**
DISCONNECTED / CONNECTING / CONNECTED / RECONNECTING / FAILED

### `Socks5Client`
```python
Socks5Client(host, port=1080, username=None, password=None, timeout=10.0)
```
- `connect()` → bool — SOCKS5 greeting + optional auth
- `close()`
- `connect_remote(host, port)` → bool — SOCKS5 CONNECT command
- `udp_associate()` → (Optional[str], Optional[int]) — SOCKS5 UDP ASSOCIATE

### `RedsocksManager`
```python
RedsocksManager(socks_host="127.0.0.1", socks_port=1080,
                socks_username=None, socks_password=None,
                local_port=12345, redsocks_binary="redsocks")
```
- `write_config()` → str — writes redsocks.conf to temp dir
- `start()` → bool — launches redsocks process
- `stop()` → bool — SIGTERM then kill
- `is_running()` → bool
- `restart()` → bool
- `status()` → dict

### `IptablesManager`
- `install_redirect()` → bool — NAT redirect rule
- `flush_redirect()` — remove rules
- `status()` → dict

### GTK GUI (`TetherClientApplication`, `TetherClientWindow`)
- `python -m ui.__main__` — launch
- Window shows connection config, status, real-time metrics, system tray

---

## Shared Models (Python)

Located in `shared/models/tether.py`.

### Enums
- `TetherRole`: HOST, CLIENT, UNKNOWN
- `ConnectionState`: DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING, FAILED
- `TetherMode`: USB, WIFI, BLUETOOTH
- `ProxyProtocol`: SOCKS5, TCP, UDP
- `BypassLayer`: TTL_NORMALIZATION, IPV6_BLOCKING, DNS_LEAK_PREVENTION, OS_UPDATE_DOMAIN_BLOCKING, MSS_MTU_CLAMPING, TRAFFIC_SHAPING

### Dataclasses

**`CarrierBypassConfig`** — defaults: ttl=65, ipv6=True, dns=True, os_update=True, mss=1360, traffic_shaping=False

**`ConnectionProfile(id, name, role, mode=None, host_address=None, host_port=1080, username=None, password=None, bypass_config=..., auto_reconnect=True, reconnect_interval=5.0, max_reconnect_attempts=10)`**
- `to_dict()` → dict
- `from_dict(d)` → ConnectionProfile

**`ConnectionInfo(state=DISCONNECTED, role=UNKNOWN, ...)`** — bytes_sent, bytes_received, latency_ms, last_error, reconnect_attempt
- `to_dict()` → dict

**`ProxyRule(id, protocol, listen_address, listen_port, remote_address, remote_port, enabled=True, udp_forwarding=False)`**

**`BandwidthSample(timestamp, bytes_sent, bytes_received, bps_sent=0.0, bps_received=0.0)`**

**`TetherInterface(name, mode, available, active=False, ip_address=None, mac_address=None, details={})`**

---

## CI/CD Endpoints

| Workflow | Path | Trigger |
|----------|------|---------|
| Android Build | `.github/workflows/android-build.yml` | push/PR main |
| Linux Build | `.github/workflows/linux-build.yml` | push/PR main |
| Integration Test | `.github/workflows/integration-test.yml` | push/PR main + nightly |
| Release | `.github/workflows/release.yml` | manual `workflow_dispatch` |

> **Note:** Workflow paths reference `tether-app/` subdirectory; actual repo has `android-app/`, `linux-client/`, `shared/`, `tests/` at root. Update paths before using CI.