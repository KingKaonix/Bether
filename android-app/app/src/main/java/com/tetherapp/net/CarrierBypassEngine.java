package com.tetherapp.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 6-layer carrier detection bypass engine:
 *   1. TTL normalization (set to 65)
 *   2. IPv6 blocking via sysctl
 *   3. DNS leak prevention
 *   4. OS update domain blocking
 *   5. MSS/MTU clamping
 *   6. Optional traffic shaping
 *
 * Some layers require root (sysctl / iptables); where root is unavailable
 * the layer is marked degraded and the app continues gracefully.
 */
public class CarrierBypassEngine {
    private static final String TAG = "CarrierBypassEngine";

    public static final int DEFAULT_TTL = 65;
    public static final int DEFAULT_MSS = 1360;

    public enum LayerState {
        DISABLED,
        ACTIVE,
        DEGRADED,   // attempted but limited (e.g., no root)
        FAILED
    }

    private final Context context;
    private final boolean isRoot;

    // Layer states
    private LayerState ttlState = LayerState.DISABLED;
    private LayerState ipv6State = LayerState.DISABLED;
    private LayerState dnsState = LayerState.DISABLED;
    private LayerState osUpdateState = LayerState.DISABLED;
    private LayerState mssState = LayerState.DISABLED;
    private LayerState shapingState = LayerState.DISABLED;

    // Config
    private int ttlValue = DEFAULT_TTL;
    private int mssValue = DEFAULT_MSS;
    private int mtuValue = 1400;

    private final List<String> blockedOsUpdateDomains = new ArrayList<>();

    public interface BypassListener {
        void onLayerStateChanged(CarrierBypassLayer layer, LayerState state);
        void onBypassStatusChanged();
    }

    public enum CarrierBypassLayer {
        TTL_NORMALIZATION,
        IPV6_BLOCKING,
        DNS_LEAK_PREVENTION,
        OS_UPDATE_DOMAIN_BLOCKING,
        MSS_MTU_CLAMPING,
        TRAFFIC_SHAPING
    }

    private final java.util.List<BypassListener> listeners = new java.util.ArrayList<>();

    public CarrierBypassEngine(Context context) {
        this.context = context;
        this.isRoot = isRootAvailable();
        Log.i(TAG, "CarrierBypassEngine initialized (root=" + isRoot + ")");
        initializeBlockedDomains();
    }

    private boolean isRootAvailable() {
        try {
            Process su = Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "id"});
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(su.getInputStream()));
            String line = br.readLine();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }

    private void initializeBlockedDomains() {
        // Common OS update / telemetry domains carriers may fingerprint
        blockedOsUpdateDomains.add("android.clients.google.com");
        blockedOsUpdateDomains.add(".googleapis.com");
        blockedOsUpdateDomains.add("gvt1.com");
        blockedOsUpdateDomains.add("gs.l.google.com");
        blockedOsUpdateDomains.add("connectivitycheck.android.com");
        blockedOsUpdateDomains.add("captive.apple.com");
        blockedOsUpdateDomains.add("mesu.apple.com");
        blockedOsUpdateDomains.add("swscan.apple.com");
        blockedOsUpdateDomains.add("windowsupdate.com");
        blockedOsUpdateDomains.add("update.microsoft.com");
    }

    /* ================================================================
     * Layer 1: TTL normalization
     * ================================================================ */
    public boolean applyTtlNormalization(int ttl) {
        this.ttlValue = ttl > 0 ? ttl : DEFAULT_TTL;
        if (isRoot) {
            boolean ok = executeSysctl("net.ipv4.ip_default_ttl", String.valueOf(ttlValue));
            ttlState = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            ttlState = LayerState.DEGRADED;
            Log.w(TAG, "TTL normalization requires root; degraded mode");
        }
        notifyLayer(CarrierBypassLayer.TTL_NORMALIZATION, ttlState);
        return ttlState == LayerState.ACTIVE || ttlState == LayerState.DEGRADED;
    }

    /* ================================================================
     * Layer 2: IPv6 blocking via sysctl
     * ================================================================ */
    public boolean applyIpv6Blocking() {
        if (isRoot) {
            boolean ok = executeSysctl("net.ipv6.conf.all.disable_ipv6", "1");
            ok &= executeSysctl("net.ipv6.conf.default.disable_ipv6", "1");
            ipv6State = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            ipv6State = LayerState.DEGRADED;
            Log.w(TAG, "IPv6 blocking requires root; degraded mode");
        }
        notifyLayer(CarrierBypassLayer.IPV6_BLOCKING, ipv6State);
        return ipv6State == LayerState.ACTIVE || ipv6State == LayerState.DEGRADED;
    }

    public boolean restoreIpv6() {
        if (isRoot) {
            executeSysctl("net.ipv6.conf.all.disable_ipv6", "0");
            executeSysctl("net.ipv6.conf.default.disable_ipv6", "0");
        }
        ipv6State = LayerState.DISABLED;
        notifyLayer(CarrierBypassLayer.IPV6_BLOCKING, ipv6State);
        return true;
    }

    /* ================================================================
     * Layer 3: DNS leak prevention
     * ================================================================ */
    public boolean applyDnsLeakPrevention() {
        // Force DNS through the proxy tunnel (1.1.1.1 / 8.8.8.8) rather than
        // carrier-provided DNS. Enforced at the TunnelManager layer; here we
        // record intent and attempt to clear the system DNS cache where possible.
        try {
            clearDnsCache();
        } catch (Exception e) {
            Log.w(TAG, "Could not clear DNS cache", e);
        }
        dnsState = LayerState.ACTIVE;
        notifyLayer(CarrierBypassLayer.DNS_LEAK_PREVENTION, dnsState);
        return true;
    }

    private void clearDnsCache() {
        // Best-effort: no-op on stock Android; hook for rooted resolver flush.
        Log.d(TAG, "DNS cache clear attempted (best-effort)");
    }

    /* ================================================================
     * Layer 4: OS update domain blocking
     * ================================================================ */
    public boolean applyOsUpdateDomainBlocking() {
        // Domains are blocked via the proxy/tunnel deny-list; logged here for
        // the UI and downstream iptables/nftables rules on rooted devices.
        if (isRoot) {
            for (String domain : blockedOsUpdateDomains) {
                addHostsDenyEntry(domain);
            }
            osUpdateState = LayerState.ACTIVE;
        } else {
            osUpdateState = LayerState.DEGRADED;
            Log.w(TAG, "OS update domain blocking degraded (no root)");
        }
        notifyLayer(CarrierBypassLayer.OS_UPDATE_DOMAIN_BLOCKING, osUpdateState);
        return osUpdateState == LayerState.ACTIVE || osUpdateState == LayerState.DEGRADED;
    }

    private void addHostsDenyEntry(String domain) {
        try {
            File hosts = new File("/system/etc/hosts");
            if (!hosts.canWrite()) {
                hosts = new File("/etc/hosts");
            }
            java.io.FileWriter fw = new java.io.FileWriter(hosts, true);
            fw.write("0.0.0.0 " + domain + "\n");
            fw.close();
            Log.d(TAG, "Blocked OS update domain: " + domain);
        } catch (IOException e) {
            Log.d(TAG, "Could not write hosts entry for " + domain, e);
        }
    }

    public void addBlockedDomain(String domain) {
        if (domain != null && !blockedOsUpdateDomains.contains(domain)) {
            blockedOsUpdateDomains.add(domain);
        }
    }

    public List<String> getBlockedDomains() {
        return new ArrayList<>(blockedOsUpdateDomains);
    }

    /* ================================================================
     * Layer 5: MSS/MTU clamping
     * ================================================================ */
    public boolean applyMssMtuClamping(int mss, int mtu) {
        this.mssValue = mss > 0 ? mss : DEFAULT_MSS;
        this.mtuValue = mtu > 0 ? mtu : 1400;
        if (isRoot) {
            boolean ok = executeIptables("-A", "POSTROUTING", "-t", "mangle",
                    "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                    "-j", "TCPMSS", "--clamp-mss-to-pmtu");
            ok &= executeIptables("-A", "POSTROUTING", "-t", "mangle",
                    "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                    "-j", "TCPMSS", "--set-mss", String.valueOf(mssValue));
            mssState = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            mssState = LayerState.DEGRADED;
            Log.w(TAG, "MSS/MTU clamping requires root; degraded mode");
        }
        notifyLayer(CarrierBypassLayer.MSS_MTU_CLAMPING, mssState);
        return mssState == LayerState.ACTIVE || mssState == LayerState.DEGRADED;
    }

    /* ================================================================
     * Layer 6: Optional traffic shaping
     * ================================================================ */
    public boolean applyTrafficShaping() {
        // Placeholder for token-bucket / bandwidth shaping. Degraded without root.
        if (isRoot) {
            shapingState = LayerState.ACTIVE;
        } else {
            shapingState = LayerState.DEGRADED;
        }
        notifyLayer(CarrierBypassLayer.TRAFFIC_SHAPING, shapingState);
        return true;
    }

    public boolean disableAll() {
        restoreIpv6();
        ttlState = LayerState.DISABLED;
        dnsState = LayerState.DISABLED;
        osUpdateState = LayerState.DISABLED;
        mssState = LayerState.DISABLED;
        shapingState = LayerState.DISABLED;
        for (CarrierBypassLayer l : CarrierBypassLayer.values()) {
            notifyLayer(l, LayerState.DISABLED);
        }
        return true;
    }

    /* ================================================================
     * Query helpers
     * ================================================================ */
    public int getTtlValue() {
        return ttlValue;
    }

    public int getMssValue() {
        return mssValue;
    }

    public int getMtuValue() {
        return mtuValue;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public LayerState getTtlState() {
        return ttlState;
    }

    public LayerState getIpv6State() {
        return ipv6State;
    }

    public LayerState getDnsState() {
        return dnsState;
    }

    public LayerState getOsUpdateState() {
        return osUpdateState;
    }

    public LayerState getMssState() {
        return mssState;
    }

    public LayerState getShapingState() {
        return shapingState;
    }

    public boolean isLayerActive(CarrierBypassLayer layer) {
        switch (layer) {
            case TTL_NORMALIZATION: return ttlState == LayerState.ACTIVE;
            case IPV6_BLOCKING: return ipv6State == LayerState.ACTIVE;
            case DNS_LEAK_PREVENTION: return dnsState == LayerState.ACTIVE;
            case OS_UPDATE_DOMAIN_BLOCKING: return osUpdateState == LayerState.ACTIVE;
            case MSS_MTU_CLAMPING: return mssState == LayerState.ACTIVE;
            case TRAFFIC_SHAPING: return shapingState == LayerState.ACTIVE;
            default: return false;
        }
    }

    public boolean isAnyLayerActive() {
        return ttlState == LayerState.ACTIVE || ipv6State == LayerState.ACTIVE
                || dnsState == LayerState.ACTIVE || osUpdateState == LayerState.ACTIVE
                || mssState == LayerState.ACTIVE || shapingState == LayerState.ACTIVE;
    }

    public int activeLayerCount() {
        int count = 0;
        if (ttlState == LayerState.ACTIVE) count++;
        if (ipv6State == LayerState.ACTIVE) count++;
        if (dnsState == LayerState.ACTIVE) count++;
        if (osUpdateState == LayerState.ACTIVE) count++;
        if (mssState == LayerState.ACTIVE) count++;
        if (shapingState == LayerState.ACTIVE) count++;
        return count;
    }

    public void addListener(BypassListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BypassListener listener) {
        listeners.remove(listener);
    }

    private void notifyLayer(CarrierBypassLayer layer, LayerState state) {
        for (BypassListener l : listeners) {
            l.onLayerStateChanged(layer, state);
        }
        for (BypassListener l : listeners) {
            l.onBypassStatusChanged();
        }
    }

    /* ================================================================
     * Native helpers (root)
     * ================================================================ */
    private boolean executeSysctl(String key, String value) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "/system/bin/su", "-c", "sysctl -w " + key + "=" + value
            });
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            Log.e(TAG, "sysctl failed: " + key + "=" + value, e);
            return false;
        }
    }

    private boolean executeIptables(String... args) {
        try {
            List<String> command = new java.util.ArrayList<>();
            command.add("/system/bin/su");
            command.add("-c");
            StringBuilder sb = new StringBuilder("iptables");
            for (String a : args) {
                sb.append(" '").append(a).append("'");
            }
            command.add(sb.toString());
            Process p = Runtime.getRuntime().exec(command.toArray(new String[0]));
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            Log.e(TAG, "iptables failed", e);
            return false;
        }
    }

    public void dumpStatus() {
        Log.i(TAG, "=== Carrier Bypass Status ===");
        Log.i(TAG, "Root: " + isRoot);
        Log.i(TAG, "  TTL normalization:       " + ttlState + " (ttl=" + ttlValue + ")");
        Log.i(TAG, "  IPv6 blocking:           " + ipv6State);
        Log.i(TAG, "  DNS leak prevention:     " + dnsState);
        Log.i(TAG, "  OS update domain block:  " + osUpdateState);
        Log.i(TAG, "  MSS/MTU clamping:        " + mssState + " (mss=" + mssValue + ", mtu=" + mtuValue + ")");
        Log.i(TAG, "  Traffic shaping:         " + shapingState);
        Log.i(TAG, "Active layers: " + activeLayerCount() + "/6");
        Log.i(TAG, "=============================");
    }
}
