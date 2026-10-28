# Bether

Unified cross-platform tethering solution — Android host + Linux client with SOCKS5 proxy, VPN tunnel, carrier bypass, and CI/CD.

## What it is

Bether lets an Android device share its internet connection with a Linux machine over USB, Wi-Fi, or Bluetooth. The Android app acts as the **tethering host** (providing internet via SOCKS5 proxy + optional system-wide VPN tunnel), and the Linux client routes all traffic through it using a SOCKS5 client, redsocks transparent proxy, and iptables NAT/mangle rules.

A **6-layer carrier bypass engine** (TTL normalization, IPv6 blocking, DNS leak prevention, OS-update domain blocking, MSS/MTU clamping, optional traffic shaping) helps evade carrier tethering detection.

## Architecture

```
┌─────────────────┐      USB / Wi-Fi / Bluetooth      ┌─────────────────┐
│   Android Host  │ ◄────────────────────────────────► │  Linux Client   │
│  (com.tetherapp)│                                    │  (Python + GTK)  │
│                 │                                    │                 │
│  • SOCKS5 proxy │◄───────────────────────────────────│  • SOCKS5 client│
│  • VPN tunnel   │                                    │  • redsocks      │
│  • Carrier bypass│                                   │  • iptables      │
│  • Hotspot/USB  │                                   │  • GTK3 GUI      │
└─────────────────┘                                    └─────────────────┘
```

### Components

| Component | Location | Language | Purpose |
|-----------|----------|----------|---------|
| Android host app | `android-app/` | Java + Gradle | Tethering host: USB/WiFi/Bluetooth, SOCKS5 proxy, VPN, carrier bypass |
| Linux client | `linux-client/` | Python 3 + GTK3 | SOCKS5 client, redsocks, iptables, cyberpunk GUI |
| Shared models | `shared/` | Python | Dataclasses serialized to JSON, shared across platforms |
| Tests | `tests/` | Python | Unit + integration tests |
| CI/CD | `.github/workflows/` | GitHub Actions | Android build, Linux build, integration tests, releases |

### Android Host (HostActivity)

- **USB tethering** — `UsbTetherManager`
- **Wi-Fi hotspot** — `WifiHotspotManager`
- **Bluetooth DUN** — `BluetoothDunManager`
- **SOCKS5 proxy** (TCP CONNECT + UDP ASSOCIATE) — `Socks5ProxyServer` on port 1080
- **VPN tunnel** (system-wide routing) — `VpnTetherService` (binds `BIND_VPN_SERVICE`)
- **Carrier bypass engine** — `CarrierBypassEngine` (6 layers)
- **Role detection** — `RoleManager` (host vs. client based on hardware)
- **Onboarding** — `OnboardingActivity` picks host/client role at first launch

### Linux Client

- **SOCKS5 client** — `socks5_client.py` (SOCKS5 CONNECT, UDP ASSOCIATE, auth)
- **Connection manager** — `linux_connection_manager.py` (lifecycle, reconnect with backoff)
- **redsocks manager** — `redsocks_manager.py` (transparent TCP proxy)
- **iptables manager** — `iptables_manager.py` (NAT/mangle redirect rules)
- **GTK3 cyberpunk GUI** — `gtk_client.py` (connect/disconnect, real-time metrics, system tray)
- **Scripts** — `connect.sh`, `disconnect.sh`, `gui.sh`

### Shared Models (`shared/models/tether.py`)

- `TetherRole` — HOST / CLIENT / UNKNOWN
- `ConnectionState` — DISCONNECTED / CONNECTING / CONNECTED / RECONNECTING / FAILED
- `TetherMode` — USB / Wi-Fi / Bluetooth
- `ConnectionProfile` — saved connection config + bypass settings
- `ConnectionInfo` — live connection state + byte counters
- `CarrierBypassConfig` — 6-layer bypass configuration (TTL=65, MSS=1360, etc.)
- `ProxyRule` / `BandwidthSample` / `TetherInterface`

## Quick Start

### Prerequisites

**Android host:**
- Android 5.0+ (API 21), USB host + Wi-Fi + Bluetooth preferred
- Root required for full carrier bypass (sysctl/iptables/hosts); degraded mode works without root

**Linux client:**
- Python 3.10+
- GTK3 + PyGObject (for GUI)
- `redsocks` binary (for transparent proxy)
- `iptables` + root (for system-wide traffic redirect)

### 1. Clone

```bash
git clone https://github.com/KingKaonix/Bether.git
cd Bether
```

### 2. Android host

Open `android-app/` in Android Studio, build, and install on your device. Launch the app, select **Host** or **Client** role, then enable the tethering methods you want (USB, Wi-Fi hotspot, Bluetooth DUN) and start the SOCKS5 proxy.

### 3. Linux client

```bash
cd linux-client
pip install -r requirements.txt  # if present; otherwise just PyGObject
python -m ui.__main__            # launch GTK GUI
# or
./scripts/connect.sh --host 192.168.43.1 --port 1080
./scripts/disconnect.sh
```

### 4. Connect

- Android host: enable USB tethering (or Wi-Fi hotspot), start SOCKS5 proxy
- Linux client: enter host IP (e.g. `192.168.43.1`) and port (`1080`), click **CONNECT**
- All Linux traffic is now routed through the Android host via SOCKS5 + redsocks + iptables

## CI/CD

| Workflow | Trigger | What it does |
|----------|---------|--------------|
| `android-build.yml` | push/PR to main | Builds debug APK on API 21/28/34, runs unit tests + lint |
| `linux-build.yml` | push/PR to main | Lints + formats Python code, runs unit tests on Python 3.10/3.11/3.12 |
| `integration-test.yml` | push/PR + nightly | Runs integration tests, validates carrier bypass config, bandit security scan |
| `release.yml` | manual dispatch | Builds release APK, creates GitHub release tag + uploads APK artifact |

> **Note:** The CI workflows reference paths under `tether-app/` (e.g. `tether-app/android-app`), but the actual repo layout has `android-app/`, `linux-client/`, `shared/`, `tests/` at the repository root. Update `.github/workflows/*.yml` working-directory and artifact paths if you intend to run CI as-is.

## Repository Layout

```
Bether/
├── README.md                    # this file
├── .github/workflows/           # GitHub Actions CI/CD
│   ├── android-build.yml
│   ├── linux-build.yml
│   ├── integration-test.yml
│   └── release.yml
├── android-app/                 # Android host app (Java + Gradle)
│   ├── app/src/main/java/com/tetherapp/
│   │   ├── net/                 # network stack (SOCKS5, VPN, bypass, USB, WiFi, BT)
│   │   └── ui/                  # activities (Onboarding, Host, Client)
│   ├── app/src/main/res/        # layouts, colors, strings, themes
│   ├── build.gradle             # app-level Gradle config
│   └── settings.gradle
├── linux-client/                # Linux client (Python + GTK3)
│   ├── src/                     # connection manager, SOCKS5, redsocks, iptables
│   ├── ui/                      # GTK3 cyberpunk GUI
│   └── scripts/                 # connect/disconnect/gui shell scripts
├── shared/                      # shared Python data models
│   └── models/tether.py         # dataclasses for roles, connections, bypass config
├── tests/                       # Python unit + integration tests
│   ├── linux_client/
│   ├── shared/
│   └── integration/
└── setup.cfg                    # pytest configuration
```

## License

MIT License — see `LICENSE` if present, or default MIT.

## Contributing

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/xxx`)
3. Commit changes (`git commit -m "feat: ..."`)
4. Push (`git push origin feature/xxx`)
5. Open a pull request

---

**Bether** — Tether anything, anywhere.