# Bether — Android Host App

Android-side tethering host for the Bether cross-platform tethering solution. Provides USB/Wi-Fi/Bluetooth tethering, SOCKS5 proxy (TCP + UDP), system-wide VPN tunnel, and a 6-layer carrier bypass engine.

## Features

| Feature | Class | Description |
|---------|-------|-------------|
| USB tethering | `UsbTetherManager` | Share internet over USB to connected devices |
| Wi-Fi hotspot | `WifiHotspotManager` | Create a Wi-Fi access point for clients |
| Bluetooth DUN | `BluetoothDunManager` | Bluetooth Dial-Up Networking tethering |
| SOCKS5 proxy | `Socks5ProxyServer` | SOCKS5 CONNECT + UDP ASSOCIATE on port 1080 |
| VPN tunnel | `VpnTetherService` | System-wide traffic routing via `VpnService` |
| Carrier bypass | `CarrierBypassEngine` | 6-layer detection evasion (see below) |
| Role detection | `RoleManager` | Auto-detect host vs. client based on hardware |
| Connection state | `ConnectionStateMachine` | Connection lifecycle with retry/backoff |
| Performance | `PerformanceMonitor` | Bandwidth + latency monitoring |
| Optimization | `OptimizationSettings` | TCP/Tuning tweaks |

## Carrier Bypass Engine (6 layers)

| Layer | What it does | Root required |
|-------|-------------|---------------|
| TTL normalization | Sets IP TTL to 65 to hide tethering hops | Yes |
| IPv6 blocking | Disables IPv6 via sysctl | Yes |
| DNS leak prevention | Forces DNS through proxy tunnel | No |
| OS update domain blocking | Blocks update/telemetry domains via /etc/hosts | Yes (for hosts file) |
| MSS/MTU clamping | Clamps TCP MSS to 1360 via iptables mangle | Yes |
| Traffic shaping | Token-bucket bandwidth shaping (placeholder) | Yes |

Without root, layers run in **degraded mode** — the app continues working but bypass effectiveness is reduced.

## UI Flow

```
OnboardingActivity  →  select HOST or CLIENT role
├── HOST  →  HostActivity  (USB/Wi-Fi/BT toggles, proxy switch, bypass switch)
└── CLIENT → ClientActivity (host/port input, connect/disconnect)
```

## Project Structure

```
android-app/
├── build.gradle                  # project-level Gradle config
├── settings.gradle               # includes ':app'
└── app/
    ├── build.gradle              # app-level config (minSdk 21, targetSdk 34)
    └── src/main/
        ├── AndroidManifest.xml   # permissions, services, activities
        ├── java/com/tetherapp/
        │   ├── TetherApplication.java   # Application singleton
        │   ├── net/
        │   │   ├── RoleManager.java          # host/client detection
        │   │   ├── ConnectionManagerService.java # foreground service stub
        │   │   ├── ConnectionStateMachine.java   # connection lifecycle
        │   │   ├── CarrierBypassEngine.java    # 6-layer bypass
        │   │   ├── Socks5ProxyServer.java      # SOCKS5 TCP+UDP proxy
        │   │   ├── Socks5Proxy.java            # proxy helper
        │   │   ├── TunnelManager.java          # VPN tunnel management
        │   │   ├── VpnTetherService.java       # VpnService binding
        │   │   ├── UsbTetherManager.java       # USB tethering
        │   │   ├── UsbTetherService.java       # USB tether service
        │   │   ├── WifiHotspotManager.java     # Wi-Fi AP
        │   │   ├── BluetoothDunManager.java    # Bluetooth DUN
        │   │   ├── NetworkInterfaceManager.java # interface enumeration
        │   │   ├── NetworkStateReceiver.java   # connectivity broadcast
        │   │   ├── PerformanceMonitor.java     # bandwidth/latency stats
        │   │   └── OptimizationSettings.java   # TCP tweaks
        │   └── ui/
        │       ├── OnboardingActivity.java   # role selection
        │       ├── HostActivity.java         # host controls
        │       └── ClientActivity.java       # client connect UI
        └── res/
            ├── layout/activity_*.xml
            ├── values/colors.xml, strings.xml, themes.xml
```

## Build

### Prerequisites
- Android Studio / Gradle
- JDK 17
- Android SDK 34 (compileSdk), minSdk 21

### Commands

```bash
cd android-app

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run unit tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug

# Clean
./gradlew clean
```

### CI

`.github/workflows/android-build.yml` builds on API 21/28/34, runs unit tests + lint, and uploads the debug APK as an artifact.

## Permissions

Key permissions declared in `AndroidManifest.xml`:

- `INTERNET`, `ACCESS_NETWORK_STATE`, `CHANGE_NETWORK_STATE`
- `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`
- `USB_PERMISSION` + `android.hardware.usb.host`
- `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`
- `BIND_VPN_SERVICE` (for VpnTetherService)
- `WRITE_SETTINGS`, `MANAGE_NETWORK_POLICY` (carrier bypass, optional)

## License

MIT