package com.tetherapp.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.TrafficStats;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Bandwidth and connection quality monitoring for performance optimization.
 *
 * Tracks bytes sent/received over time, computes instantaneous bitrates,
 * estimates latency/quality, and exposes optimization recommendations
 * (e.g., low-latency mode for gaming/video).
 */
public class PerformanceMonitor {
    private static final String TAG = "PerformanceMonitor";

    private final Context context;
    private final SharedPreferences prefs;

    private long lastBytesSent = 0;
    private long lastBytesRecv = 0;
    private long lastTimestamp = 0;

    private final List<BandwidthSample> samples = new CopyOnWriteArrayList<>();
    private final int maxSamples = 60; // ~1 minute at 1s intervals

    private volatile boolean monitoring = false;
    private Thread monitorThread;

    private ConnectionQuality currentQuality = ConnectionQuality.UNKNOWN;

    public enum ConnectionQuality {
        EXCELLENT,   // > 10 Mbps, low latency
        GOOD,        // 2-10 Mbps
        FAIR,        // 500 Kbps - 2 Mbps
        POOR,        // < 500 Kbps
        UNKNOWN
    }

    public interface QualityListener {
        void onQualityChanged(ConnectionQuality quality);
        void onBandwidthSample(BandwidthSample sample);
    }

    private final java.util.List<QualityListener> listeners = new java.util.ArrayList<>();

    public PerformanceMonitor(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences("tetherapp_perf", Context.MODE_PRIVATE);
    }

    public void startMonitoring() {
        if (monitoring) {
            return;
        }
        monitoring = true;
        lastBytesSent = TrafficStats.getTotalTxBytes();
        lastBytesRecv = TrafficStats.getTotalRxBytes();
        lastTimestamp = System.currentTimeMillis();
        monitorThread = new Thread(this::monitorLoop, "perf-monitor");
        monitorThread.setDaemon(true);
        monitorThread.start();
        Log.d(TAG, "Performance monitoring started");
    }

    public void stopMonitoring() {
        monitoring = false;
        if (monitorThread != null) {
            monitorThread.interrupt();
        }
        Log.d(TAG, "Performance monitoring stopped");
    }

    public boolean isMonitoring() {
        return monitoring;
    }

    private void monitorLoop() {
        while (monitoring && !Thread.interrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            long now = System.currentTimeMillis();
            long tx = TrafficStats.getTotalTxBytes();
            long rx = TrafficStats.getTotalRxBytes();

            long dt = now - lastTimestamp;
            if (dt > 0 && lastTimestamp != 0) {
                double bpsSent = (tx - lastBytesSent) * 1000.0 / dt;
                double bpsRecv = (rx - lastBytesRecv) * 1000.0 / dt;
                BandwidthSample sample = new BandwidthSample(now,
                        tx - lastBytesSent, rx - lastBytesRecv, bpsSent, bpsRecv);
                samples.add(sample);
                if (samples.size() > maxSamples) {
                    samples.remove(0);
                }
                currentQuality = assessQuality(bpsSent + bpsRecv);
                for (QualityListener l : listeners) {
                    l.onBandwidthSample(sample);
                }
                for (QualityListener l : listeners) {
                    l.onQualityChanged(currentQuality);
                }
            }
            lastBytesSent = tx;
            lastBytesRecv = rx;
            lastTimestamp = now;
        }
    }

    private ConnectionQuality assessQuality(double totalBps) {
        double kbps = totalBps / 1024.0;
        if (kbps > 10240) {      // > 10 Mbps
            return ConnectionQuality.EXCELLENT;
        } else if (kbps > 2048) { // > 2 Mbps
            return ConnectionQuality.GOOD;
        } else if (kbps > 512) {  // > 500 Kbps
            return ConnectionQuality.FAIR;
        } else if (kbps > 0) {
            return ConnectionQuality.POOR;
        }
        return ConnectionQuality.UNKNOWN;
    }

    public ConnectionQuality getCurrentQuality() {
        return currentQuality;
    }

    public List<BandwidthSample> getSamples() {
        return new ArrayList<>(samples);
    }

    public BandwidthSample getLatestSample() {
        if (samples.isEmpty()) {
            return null;
        }
        return samples.get(samples.size() - 1);
    }

    public double getCurrentBitrateBps() {
        BandwidthSample s = getLatestSample();
        return s != null ? s.getBitsPerSecondSent() + s.getBitsPerSecondReceived() : 0;
    }

    public void addListener(QualityListener listener) {
        listeners.add(listener);
    }

    public void removeListener(QualityListener listener) {
        listeners.remove(listener);
    }

    public void reset() {
        samples.clear();
        lastBytesSent = TrafficStats.getTotalTxBytes();
        lastBytesRecv = TrafficStats.getTotalRxBytes();
        lastTimestamp = System.currentTimeMillis();
        currentQuality = ConnectionQuality.UNKNOWN;
    }

    public boolean isLowLatencyMode() {
        return prefs.getBoolean("low_latency_mode", false);
    }

    public void setLowLatencyMode(boolean enabled) {
        prefs.edit().putBoolean("low_latency_mode", enabled).apply();
        Log.i(TAG, "Low-latency mode: " + enabled);
    }

    public boolean isAutoOptimize() {
        return prefs.getBoolean("auto_optimize", true);
    }

    public void setAutoOptimize(boolean enabled) {
        prefs.edit().putBoolean("auto_optimize", enabled).apply();
    }

    public int getThrottleKbps() {
        return prefs.getInt("throttle_kbps", 0); // 0 = unlimited
    }

    public void setThrottleKbps(int kbps) {
        prefs.edit().putInt("throttle_kbps", kbps).apply();
    }

    public String getQualityLabel() {
        switch (currentQuality) {
            case EXCELLENT: return "Excellent";
            case GOOD: return "Good";
            case FAIR: return "Fair";
            case POOR: return "Poor";
            default: return "Unknown";
        }
    }
}
