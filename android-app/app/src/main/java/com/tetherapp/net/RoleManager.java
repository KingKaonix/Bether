package com.tetherapp.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.usb.UsbManager;
import android.net.WifiManager;
import android.bluetooth.BluetoothAdapter;
import android.util.Log;

/**
 * Runtime role detection for the unified APK.
 *
 * The single APK serves both host (tethering provider) and client
 * (consumer) modes. The role is detected at runtime from available
 * hardware and persisted user preference, then the UI adapts.
 */
public class RoleManager {
    private static final String TAG = "RoleManager";
    private static final String PREFS_NAME = "tetherapp_prefs";
    private static final String KEY_ROLE = "tether_role";
    private static final String KEY_LAST_DETECTED = "last_detected_role";

    public enum DeviceRole {
        HOST,      // provides internet (tethering host)
        CLIENT,    // needs internet from host
        UNKNOWN
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final UsbManager usbManager;
    private final WifiManager wifiManager;
    private final BluetoothAdapter bluetoothAdapter;

    private DeviceRole detectedRole = DeviceRole.UNKNOWN;

    public RoleManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /**
     * Detect the device's tethering capability at runtime.
     * Host-capable if it has USB host + WiFi hotspot + Bluetooth.
     */
    public DeviceRole detectRole() {
        boolean hasUsbHost = usbManager != null;
        boolean hasWifiAp = wifiManager != null;
        boolean hasBt = bluetoothAdapter != null;

        int hostScore = 0;
        if (hasUsbHost) hostScore++;
        if (hasWifiAp) hostScore++;
        if (hasBt) hostScore++;

        boolean hostCapable = hostScore >= 2; // at least 2 tethering radios

        // Persisted preference overrides detection if user explicitly chose
        String saved = prefs.getString(KEY_ROLE, null);
        DeviceRole preferred = saved != null ? DeviceRole.valueOf(saved) : null;

        if (preferred != null) {
            detectedRole = preferred;
        } else if (hostCapable) {
            // Default to host if capable, but allow client mode
            detectedRole = DeviceRole.HOST;
        } else {
            detectedRole = DeviceRole.CLIENT;
        }

        prefs.edit().putString(KEY_LAST_DETECTED, detectedRole.name()).apply();
        Log.i(TAG, "Detected role: " + detectedRole + " (hostCapable=" + hostCapable + ")");
        return detectedRole;
    }

    public DeviceRole getDetectedRole() {
        return detectedRole;
    }

    public boolean isHost() {
        return detectedRole == DeviceRole.HOST;
    }

    public boolean isClient() {
        return detectedRole == DeviceRole.CLIENT;
    }

    public void setRole(DeviceRole role) {
        detectedRole = role;
        prefs.edit().putString(KEY_ROLE, role.name()).apply();
        Log.i(TAG, "Role set to: " + role);
    }

    public void clearPreferredRole() {
        prefs.edit().remove(KEY_ROLE).apply();
        detectRole(); // re-detect
    }

    public boolean isHostCapable() {
        boolean hasUsb = usbManager != null;
        boolean hasWifi = wifiManager != null;
        boolean hasBt = bluetoothAdapter != null;
        return (hasUsb ? 1 : 0) + (hasWifi ? 1 : 0) + (hasBt ? 1 : 0) >= 2;
    }

    public String getRoleSummary() {
        switch (detectedRole) {
            case HOST: return "Tethering Host — provides internet";
            case CLIENT: return "Client — needs internet from host";
            default: return "Role undetected";
        }
    }
}
