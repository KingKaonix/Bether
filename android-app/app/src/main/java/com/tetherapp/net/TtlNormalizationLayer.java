package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

/**
 * Layer 1: TTL normalization. Sets net.ipv4.ip_default_ttl to hide tethering hops.
 * Requires root for full enforcement; degraded mode logs intent without sysctl.
 */
public class TtlNormalizationLayer implements BypassLayer {

    private static final String TAG = "TtlNormalizationLayer";
    private static final int DEFAULT_TTL = 65;

    private final Context context;
    private final CarrierBypassEngine.RootExecutor rootExecutor;
    private final boolean isRoot;
    private int ttlValue = DEFAULT_TTL;
    private LayerState state = LayerState.DISABLED;

    public TtlNormalizationLayer(Context context, CarrierBypassEngine.RootExecutor rootExecutor) {
        this.context = context;
        this.rootExecutor = rootExecutor;
        this.isRoot = isRootAvailable();
    }

    @Override
    public Type getType() {
        return Type.TTL_NORMALIZATION;
    }

    @Override
    public String getName() {
        return "TTL Normalization";
    }

    @Override
    public boolean requiresRoot() {
        return true;
    }

    @Override
    public LayerState getState() {
        return state;
    }

    public int getTtlValue() {
        return ttlValue;
    }

    @Override
    public LayerState apply() {
        if (isRoot) {
            boolean ok = rootExecutor != null && rootExecutor.executeRootCommand(
                    "TTL Normalization (sysctl net.ipv4.ip_default_ttl=" + ttlValue + ")",
                    () -> executeSysctl("net.ipv4.ip_default_ttl", String.valueOf(ttlValue))
            );
            state = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            state = LayerState.DEGRADED;
            Log.w(TAG, "TTL normalization requires root; degraded mode");
        }
        return state;
    }

    @Override
    public void revert() {
        if (isRoot && rootExecutor != null) {
            rootExecutor.executeRootCommand(
                    "Revert TTL Normalization (sysctl net.ipv4.ip_default_ttl=64)",
                    () -> executeSysctl("net.ipv4.ip_default_ttl", "64")
            );
        }
        state = LayerState.DISABLED;
    }

    public void setTtlValue(int ttl) {
        this.ttlValue = ttl > 0 ? ttl : DEFAULT_TTL;
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
}
