package com.tetherapp.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Optimization settings for the tethering connection.
 *
 * Includes bandwidth throttling, auto-optimization based on connection
 * quality, and a low-latency mode for gaming/video.
 */
public class OptimizationSettings {
    private static final String TAG = "OptimizationSettings";

    private static final String PREFS_NAME = "tetherapp_optim";
    private static final int DEFAULT_THROTTLE_KBPS = 0; // unlimited
    private static final boolean DEFAULT_AUTO_OPTIMIZE = true;
    private static final boolean DEFAULT_LOW_LATENCY = false;

    private final SharedPreferences prefs;

    public OptimizationSettings(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /* ---- Throttling ---- */
    public int getThrottleKbps() {
        return prefs.getInt("throttle_kbps", DEFAULT_THROTTLE_KBPS);
    }

    public void setThrottleKbps(int kbps) {
        if (kbps < 0) {
            kbps = 0;
        }
        prefs.edit().putInt("throttle_kbps", kbps).apply();
        Log.i(TAG, "Throttle set to " + kbps + " kbps (" + (kbps == 0 ? "unlimited" : "limited") + ")");
    }

    public boolean isThrottled() {
        return getThrottleKbps() > 0;
    }

    /* ---- Auto-optimize ---- */
    public boolean isAutoOptimize() {
        return prefs.getBoolean("auto_optimize", DEFAULT_AUTO_OPTIMIZE);
    }

    public void setAutoOptimize(boolean enabled) {
        prefs.edit().putBoolean("auto_optimize", enabled).apply();
        Log.i(TAG, "Auto-optimize: " + enabled);
    }

    /* ---- Low-latency mode ---- */
    public boolean isLowLatencyMode() {
        return prefs.getBoolean("low_latency_mode", DEFAULT_LOW_LATENCY);
    }

    public void setLowLatencyMode(boolean enabled) {
        prefs.edit().putBoolean("low_latency_mode", enabled).apply();
        Log.i(TAG, "Low-latency mode: " + enabled);
    }

    /* ---- Derived recommendations ---- */
    /**
     * Returns a recommended MSS value based on current settings.
     * Low-latency mode uses a smaller MSS to reduce head-of-line blocking.
     */
    public int getRecommendedMss() {
        if (isLowLatencyMode()) {
            return 1024; // smaller segments for lower latency
        }
        return 1360; // default
    }

    /**
     * Returns whether TCP_NODELAY should be set (low-latency mode).
     */
    public boolean shouldUseTcpNoDelay() {
        return isLowLatencyMode();
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Throttle: ").append(isThrottled() ? getThrottleKbps() + " kbps" : "unlimited").append("\n");
        sb.append("Auto-optimize: ").append(isAutoOptimize()).append("\n");
        sb.append("Low-latency mode: ").append(isLowLatencyMode()).append("\n");
        sb.append("Recommended MSS: ").append(getRecommendedMss());
        return sb.toString();
    }
}
