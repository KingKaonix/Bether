package com.tetherapp.net;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Configures WiFi hotspot / soft AP for WiFi tethering.
 * Uses reflection where the public HotspotManager API is unavailable.
 * Implements {@link TetherInterface} for polymorphic transport handling.
 */
public class WifiHotspotManager implements TetherInterface {
    private static final String TAG = "WifiHotspotManager";

    private final Context context;
    private final WifiManager wifiManager;

    private boolean hotspotActive = false;
    private String hotspotSsid = "KAOSNET";
    private String hotspotPassphrase = "tetherapp123";

    private final java.util.List<HotspotListener> hotspotListeners = new java.util.ArrayList<>();
    private final List<TetherListener> tetherListeners = new ArrayList<>();

    public interface HotspotListener {
        void onHotspotStateChanged(boolean active);
        void onHotspotConfiguration(String ssid, String passphrase);
    }

    public WifiHotspotManager(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

    /* ---------- TetherInterface implementation ---------- */

    @Override
    public String getName() {
        return "Wi-Fi Hotspot";
    }

    @Override
    public TetherMode getMode() {
        return TetherMode.WIFI;
    }

    @Override
    public boolean isTetheringActive() {
        return hotspotActive;
    }

    @Override
    public boolean startTethering() {
        return startHotspot();
    }

    @Override
    public boolean stopTethering() {
        return stopHotspot();
    }

    @Override
    public void addListener(TetherListener listener) {
        tetherListeners.add(listener);
    }

    @Override
    public void removeListener(TetherListener listener) {
        tetherListeners.remove(listener);
    }

    @Override
    public void shutdown() {
        stopHotspot();
    }

    /* ---------- WiFi-specific API ---------- */

    public boolean isWifiEnabled() {
        return wifiManager != null && wifiManager.isWifiEnabled();
    }

    public boolean setWifiEnabled(boolean enabled) {
        if (wifiManager == null) {
            return false;
        }
        return wifiManager.setWifiEnabled(enabled);
    }

    public boolean configureHotspot(String ssid, String passphrase) {
        this.hotspotSsid = ssid != null ? ssid : "TetherApp-Hotspot";
        this.hotspotPassphrase = passphrase != null ? passphrase : "tetherapp123";
        notifyConfiguration();
        return true;
    }

    public boolean startHotspot() {
        if (wifiManager == null) {
            return false;
        }
        try {
            boolean started = startHotspotViaReflection();
            if (started) {
                hotspotActive = true;
                notifyTetheringStarted();
                notifyStateChanged();
                Log.i(TAG, "WiFi hotspot started: " + hotspotSsid);
            }
            return started;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WiFi hotspot", e);
            notifyError(e.getMessage());
            return false;
        }
    }

    public boolean stopHotspot() {
        if (wifiManager == null) {
            return false;
        }
        try {
            boolean stopped = stopHotspotViaReflection();
            if (stopped) {
                boolean wasActive = hotspotActive;
                hotspotActive = false;
                if (wasActive) {
                    notifyTetheringStopped();
                    notifyStateChanged();
                }
                Log.i(TAG, "WiFi hotspot stopped");
            }
            return stopped;
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop WiFi hotspot", e);
            return false;
        }
    }

    public String getHotspotSsid() {
        return hotspotSsid;
    }

    public String getHotspotPassphrase() {
        return hotspotPassphrase;
    }

    private boolean startHotspotViaReflection() throws Exception {
        Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
        WifiConfiguration config = buildHotspotConfig();
        Object result = method.invoke(wifiManager, config, true);
        return result instanceof Boolean && (Boolean) result;
    }

    private boolean stopHotspotViaReflection() throws Exception {
        Method method = wifiManager.getClass().getMethod("setWifiApEnabled", WifiConfiguration.class, boolean.class);
        Object result = method.invoke(wifiManager, null, false);
        return result instanceof Boolean && (Boolean) result;
    }

    private WifiConfiguration buildHotspotConfig() {
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = "\"" + hotspotSsid + "\"";
        config.preSharedKey = "\"" + hotspotPassphrase + "\"";
        config.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
        config.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.CCMP);
        config.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
        config.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
        return config;
    }

    private void notifyStateChanged() {
        for (HotspotListener l : hotspotListeners) {
            l.onHotspotStateChanged(hotspotActive);
        }
    }

    private void notifyConfiguration() {
        for (HotspotListener l : hotspotListeners) {
            l.onHotspotConfiguration(hotspotSsid, hotspotPassphrase);
        }
    }

    private void notifyTetheringStarted() {
        for (TetherListener l : tetherListeners) {
            l.onTetheringStarted(getName());
        }
    }

    private void notifyTetheringStopped() {
        for (TetherListener l : tetherListeners) {
            l.onTetheringStopped(getName());
        }
    }

    private void notifyError(String message) {
        for (TetherListener l : tetherListeners) {
            l.onError(getName(), message);
        }
    }

    public void addHotspotListener(HotspotListener listener) {
        hotspotListeners.add(listener);
    }

    public void removeHotspotListener(HotspotListener listener) {
        hotspotListeners.remove(listener);
    }

    public List<WifiConfiguration> getConfiguredNetworks() {
        return wifiManager != null ? wifiManager.getConfiguredNetworks() : new java.util.ArrayList<>();
    }
}