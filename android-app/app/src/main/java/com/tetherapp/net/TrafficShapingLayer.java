package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

/**
 * Layer 6: Optional traffic shaping (token-bucket bandwidth shaping).
 * Placeholder implementation; requires root for full enforcement.
 */
public class TrafficShapingLayer implements BypassLayer {

    private static final String TAG = "TrafficShapingLayer";
    private final Context context;
    private final CarrierBypassEngine.RootExecutor rootExecutor;
    private final boolean isRoot;
    private LayerState state = LayerState.DISABLED;

    public TrafficShapingLayer(Context context, CarrierBypassEngine.RootExecutor rootExecutor) {
        this.context = context;
        this.rootExecutor = rootExecutor;
        this.isRoot = isRootAvailable();
    }

    @Override
    public Type getType() {
        return Type.TRAFFIC_SHAPING;
    }

    @Override
    public String getName() {
        return "Traffic Shaping";
    }

    @Override
    public boolean requiresRoot() {
        return true;
    }

    @Override
    public LayerState getState() {
        return state;
    }

    @Override
    public LayerState apply() {
        if (isRoot) {
            boolean ok = rootExecutor != null && rootExecutor.executeRootCommand(
                    "Traffic Shaping Activation",
                    () -> {
                        Log.i(TAG, "Traffic shaping activated (placeholder)");
                        return true;
                    }
            );
            state = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            state = LayerState.DEGRADED;
            Log.w(TAG, "Traffic shaping degraded (no root)");
        }
        return state;
    }

    @Override
    public void revert() {
        state = LayerState.DISABLED;
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
}
