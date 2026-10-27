package com.tetherapp.net;

import android.content.Context;
import android.net.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Configures WiFi hotspot / soft AP for WiFi tethering.
 * Uses reflection where the public HotspotManager API is unavailable.
 */
public class WifiHotspotManager {
    private static final String TAG = "WifiHotspotManager";

    private final Context context;
    private final WifiManager wifiManager;

    private boolean hotspotActive = false;
    private String hotspotSsid = "TetherApp-Hotspot";
    private String hotspotPassphrase = "tetherapp123";

    public interface HotspotListener {
        void onHotspotStateChanged(boolean active);
        void onHotspotConfiguration(String ssid, String passphrase);
    }

    private final java.util.List<HotspotListener> listeners = new java.util.ArrayList<>();

    public WifiHotspotManager(Context context) {
        this.context = context;
        this.wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
    }

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
                notifyStateChanged();
                Log.i(TAG, "WiFi hotspot started: " + hotspotSsid);
            }
            return started;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start WiFi hotspot", e);
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
                hotspotActive = false;
                notifyStateChanged();
                Log.i(TAG, "WiFi hotspot stopped");
            }
            return stopped;
        } catch (Exception e) {
            Log.e(TAG, "Failed to stop WiFi hotspot", e);
            return false;
        }
    }

    public boolean isHotspotActive() {
        return hotspotActive;
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
        for (HotspotListener l : listeners) {
            l.onHotspotStateChanged(hotspotActive);
        }
    }

    private void notifyConfiguration() {
        for (HotspotListener l : listeners) {
            l.onHotspotConfiguration(hotspotSsid, hotspotPassphrase);
        }
    }

    public void addListener(HotspotListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HotspotListener listener) {
        listeners.remove(listener);
    }

    public List<WifiConfiguration> getConfiguredNetworks() {
        return wifiManager != null ? wifiManager.getConfiguredNetworks() : new java.util.ArrayList<>();
    }
}
