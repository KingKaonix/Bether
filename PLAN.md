# Bether — Improvement Plan

**Status:** Draft — pending user approval. No repository changes have been made.

---

## Phase 0 — Fix Broken CI/CD (Blocker)

| # | Issue | Fix |
|---|-------|-----|
| 0.1 | All 4 workflows reference `tether-app/` as a path prefix, but `android-app/`, `linux-client/`, `shared/`, `tests/` live at repo root | Update `working-directory`, `path`, and `sys.path` references in every `.yml` to match actual layout |
| 0.2 | `release.yml` hardcodes version `rc-1.0` as default input | Make version input required, remove default; tag from input only |
| 0.3 | `release.yml` sets git identity to `opencode` (generic) | Use `github-actions[bot]` or allow input override |

---

## Phase 1 — Complete Release APK CI/CD Pipeline

**Goal:** A single, reliable GitHub Actions workflow that builds a signed release APK, runs pre-release checks, tags the repo, creates a GitHub Release, and uploads the APK as both a release asset and a workflow artifact.

### 1.1 Workflow: `release.yml` (rewritten)

**Trigger:** `workflow_dispatch` with required `version` input (e.g. `1.0.0`, `rc-1.0`, `v2.1.3`).

**Jobs:**

| Job | Purpose | Key Steps |
|-----|---------|-----------|
| `validate` | Fail fast before any build | Checkout repo, validate version input format (semver-ish), check `android-app/` exists |
| `test` | Run Android unit tests + lint | JDK 17, Android SDK 34, `./gradlew testDebugUnitTest`, `./gradlew lintDebug` |
| `build-release` | Build signed release APK | JDK 17, Android SDK 34, Gradle 8.2, `./gradlew assembleRelease`, sign with keystore from secrets |
| `release` | Tag + create GitHub Release + upload | Create annotated git tag, push tag, create GitHub Release, upload APK as release asset + artifact |

**Steps detail (build-release):**

```yaml
- name: Set up JDK 17
  uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: 17

- name: Install Android SDK command-line tools
  run: |
    mkdir -p $HOME/android-sdk/cmdline-tools
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q commandlinetools-linux-11076708_latest.zip -d $HOME/android-sdk/cmdline-tools
    mv $HOME/android-sdk/cmdline-tools/cmdline-tools $HOME/android-sdk/cmdline-tools/latest
    echo "ANDROID_HOME=$HOME/android-sdk" >> $GITHUB_ENV
    echo "$HOME/android-sdk/cmdline-tools/latest/bin" >> $GITHUB_PATH
    echo "$HOME/android-sdk/platform-tools" >> $GITHUB_PATH
    yes | sdkmanager --licenses > /dev/null 2>&1 || true
    sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"

- name: Install Gradle
  run: |
    wget -q https://services.gradle.org/distributions/gradle-8.2-bin.zip
    unzip -q gradle-8.2-bin.zip
    echo "GRADLE_HOME=$PWD/gradle-8.2" >> $GITHUB_ENV
    echo "$PWD/gradle-8.2/bin" >> $GITHUB_PATH

- name: Configure release signing
  run: |
    echo "${{ secrets.RELEASE_KEYSTORE }}" | base64 -d > android-app/app/release-keystore.jks
    cat >> android-app/app/signing.properties <<EOF
    storeFile=release-keystore.jks
    storePassword=${{ secrets.RELEASE_STORE_PASSWORD }}
    keyAlias=${{ secrets.RELEASE_KEY_ALIAS }}
    keyPassword=${{ secrets.RELEASE_KEY_PASSWORD }}
    EOF

- name: Build Android release APK
  working-directory: android-app
  run: ./gradlew assembleRelease

- name: Verify APK exists
  run: test -f android-app/app/build/outputs/apk/release/app-release.apk
```

**Required secrets:**
- `RELEASE_KEYSTORE` — base64-encoded JKS keystore file
- `RELEASE_STORE_PASSWORD` — keystore password
- `RELEASE_KEY_ALIAS` — key alias
- `RELEASE_KEY_PASSWORD` — key password

**Steps detail (release):**

```yaml
- name: Checkout repository (full history)
  uses: actions/checkout@v4
  with:
    fetch-depth: 0
    token: ${{ secrets.GITHUB_TOKEN }}

- name: Set git identity
  run: |
    git config user.name "github-actions[bot]"
    git config user.email "github-actions[bot]@users.noreply.github.com"

- name: Create release tag
  run: |
    TAG="${{ github.event.inputs.version }}"
    git tag -a "$TAG" -m "Release $TAG"
    git push origin "$TAG"

- name: Create GitHub Release and upload APK
  uses: softprops/action-gh-release@v1
  env:
    GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
  with:
    tag_name: "${{ github.event.inputs.version }}"
    name: "Bether ${{ github.event.inputs.version }}"
    body: |
      ## Bether ${{ github.event.inputs.version }}

      ### Included
      - Android host: release APK (arm64-v8a, armeabi-v7a, x86_64)
      - SOCKS5 proxy (TCP + UDP), VPN tunnel, carrier bypass engine
      - Built from commit ${{ github.sha }}

    draft: false
    prerelease: ${{ contains(github.event.inputs.version, 'rc') || contains(github.event.inputs.version, 'beta') }}
    files: android-app/app/build/outputs/apk/release/app-release.apk

- name: Upload APK as workflow artifact
  uses: actions/upload-artifact@v4
  with:
    name: bether-release-apk-${{ github.event.inputs.version }}
    path: android-app/app/build/outputs/apk/release/app-release.apk
    retention-days: 90
```

### 1.2 Required Repository Secrets

| Secret | Description | Example |
|--------|-------------|---------|
| `RELEASE_KEYSTORE` | Base64-encoded release signing keystore (.jks) | `base64 -w0 my-release.keystore` |
| `RELEASE_STORE_PASSWORD` | Keystore store password | `...` |
| `RELEASE_KEY_ALIAS` | Key alias in keystore | `bether-release` |
| `RELEASE_KEY_PASSWORD` | Key password | `...` |

### 1.3 Required Local Changes (not yet made)

- `android-app/app/build.gradle` — add `signingConfigs` block referencing `signing.properties`; apply to `release` buildType
- `android-app/app/signing.properties` — gitignored template; generated at CI runtime from secrets
- `.gitignore` — add `android-app/app/signing.properties`, `android-app/app/release-keystore.jks`

### 1.4 Version Tag Convention

- `1.0.0` → stable release (not prerelease)
- `rc-1.0`, `rc-2.0` → release candidate (prerelease = true)
- `beta-1.0` → beta (prerelease = true)
- `v1.0.0` → accepted (leading `v` stripped for tag comparison)

---

## Phase 2 — Architecture & Decoupling

| # | Area | Current Problem | Better Approach |
|---|------|----------------|-----------------|
| 2.1 | Android host lifecycle | `HostActivity.onDestroy()` stops USB, Wi-Fi, Bluetooth, proxy, bypass unconditionally | Tethering services should outlive the UI; move to a persistent foreground service with a notification, UI just controls it |
| 2.2 | SOCKS5 proxy coupling | `Socks5ProxyServer` instantiated inside `HostActivity` | Extract into standalone `ProxyService` (bound service) so proxy runs independently of which Activity is visible |
| 2.3 | Tethering methods | `UsbTetherManager`, `WifiHotspotManager`, `BluetoothDunManager` are siloed classes with no common interface | Define a `TetherInterface` interface (`start()`, `stop()`, `isActive()`) so new transports can be added without touching HostActivity |
| 2.4 | Carrier bypass | `CarrierBypassEngine` is one monolithic class; all 6 layers coupled; root check scattered | Split into pluggable `BypassLayer` implementations, each with `apply()` / `revert()` / `requiresRoot()`. Engine composes them |
| 2.5 | Linux client stack | `linux_connection_manager.py` hardcodes redsocks + iptables; no fallback | Abstract into `Redirector` interface (redsocks, nftables, or userspace tun). Client picks available one at runtime |

---

## Phase 3 — Security

| # | Issue | Fix |
|---|-------|-----|
| 3.1 | SOCKS5 proxy binds to all interfaces | Bind to `127.0.0.1` by default; add optional `bindAddress` config for LAN exposure |
| 3.2 | No authentication on SOCKS5 by default | Require username/password auth by default; no-auth mode behind explicit opt-in flag |
| 3.3 | No transport encryption between Linux client and Android host | Add optional TLS wrapping over the SOCKS5 connection (self-signed cert pinned, or TOFU) |
| 3.4 | Passwords stored in plain text in `ConnectionProfile` | Encrypt at rest on Linux (keyring/secret-service) and Android (EncryptedSharedPreferences) |
| 3.5 | Carrier bypass writes to `/system/etc/hosts` and runs iptables as root without confirmation | Add root availability check up front; degrade gracefully with user-visible warning instead of silent failure |

---

## Phase 4 — Code Quality & Test Coverage

| # | Area | Gap | Plan |
|---|------|-----|------|
| 4.1 | Android tests | Espresso deps declared but no test classes exist | Add instrumented tests for RoleManager, CarrierBypassEngine (mocked), Socks5ProxyServer |
| 4.2 | Android docs | Zero KDoc/JavaDoc on any class | Add KDoc to all public APIs; generate Dokka HTML in CI |
| 4.3 | Python typing | Mixed typing; `connection_manager.py` unread | Full type hints + docstrings on all public modules; run `mypy` in CI |
| 4.4 | Python linting | CI runs flake8 + black but no `requirements.txt` or `pyproject.toml` | Add `pyproject.toml` with ruff/mypy/pytest config; replace flake8+black with ruff |
| 4.5 | Error handling | Many silent `catch (Exception ignored)`, `|| true` in shell | Replace swallowed errors with logged, user-visible status; propagate errors up the stack |
| 4.6 | Magic values | Port 1080, TTL 65, MSS 1360 hardcoded in multiple places | Single `Config` / `Constants` source of truth per platform |

---

## Phase 5 — DevOps & Packaging

| # | Item | Plan |
|---|------|------|
| 5.1 | Linux client deps | Add `requirements.txt` (PyGObject, pyyaml) and `requirements-dev.txt` (pytest, ruff, mypy, bandit) |
| 5.2 | Docker | Add `Dockerfile` + `docker-compose.yml` for Linux client testing without redsocks/root |
| 5.3 | Android build | CI currently builds debug only; add release signing config option (keystore from secrets) |
| 5.4 | Pre-commit | Add `.pre-commit-config.yaml` (ruff, black, ktlint/detekt, git secret scan) |
| 5.5 | Versioning | Add `gradle.properties` `VERSION_NAME`; use it in both Android `versionName` and release tag |

---

## Phase 6 — UX / Features (Post-Approval)

| # | Feature |
|---|---------|
| 6.1 | Configuration file for Android host (`tetherapp.conf`) instead of UI-only toggles |
| 6.2 | Linux client: persist last-connected host/port in `~/.config/tetherapp/` |
| 6.3 | System tray / menu bar app for Linux (AppIndicator) instead of only GTK window |
| 6.4 | Bandwidth graph in GTK GUI (currently shows raw socket buffer size, not actual throughput) |
| 6.5 | Auto-detect Android host IP on local network (mDNS / broadcast discovery) |

---

## Estimated Effort

| Phase | Size | Risk |
|-------|------|------|
| Phase 0 (CI fix) | Small | Low — pure path/string edits |
| Phase 1 (release pipeline) | Medium | Medium — signing keystore management; needs secret setup |
| Phase 2 (architecture) | Large | Medium — touches core flow; needs regression testing |
| Phase 3 (security) | Medium | Medium — auth/TLS changes break existing client-host pairing; migration needed |
| Phase 4 (quality) | Medium | Low — additive |
| Phase 5 (devops) | Small | Low |
| Phase 6 (features) | Large | Low — optional |

---

## Approval Checklist

- [ ] Phase 0 (CI path fixes) approved
- [ ] Phase 1 (release APK pipeline) approved
- [ ] Phase 2 (architecture) approved
- [ ] Phase 3 (security) approved
- [ ] Phase 4 (quality) approved
- [ ] Phase 5 (devops) approved
- [ ] Phase 6 (features) approved

**No changes will be made to the repository until you explicitly approve each phase.**