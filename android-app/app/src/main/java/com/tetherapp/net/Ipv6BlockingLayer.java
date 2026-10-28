package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

/**
 * Layer 2: IPv6 blocking via sysctl. Requires root.
 */
public class Ipv6BlockingLayer implements BypassLayer {

    private static final String TAG = "Ipv6BlockingLayer";
    private final Context context;
    private final boolean isRoot;
    private LayerState state = LayerState.DISABLED;

    public Ipv6BlockingLayer(Context context) {
        this.context = context;
        this.isRoot = isRootAvailable();
    }

    @Override
    public Type getType() {
        return Type.IPV6_BLOCKING;
    }

    @Override
    public String getName() {
        return "IPv6 Blocking";
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
            boolean ok = executeSysctl("net.ipv6.conf.all.disable_ipv6", "1");
            ok &= executeSysctl("net.ipv6.conf.default.disable_ipv6", "1");
            state = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            state = LayerState.DEGRADED;
            Log.w(TAG, "IPv6 blocking requires root; degraded mode");
        }
        return state;
    }

    @Override
    public void revert() {
        if (isRoot) {
            executeSysctl("net.ipv6.conf.all.disable_ipv6", "0");
            executeSysctl("net.ipv6.conf.default.disable_ipv6", "0");
        }
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