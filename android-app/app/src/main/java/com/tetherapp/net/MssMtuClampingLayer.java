package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * Layer 5: MSS/MTU clamping via iptables mangle table. Requires root.
 */
public class MssMtuClampingLayer implements BypassLayer {

    private static final String TAG = "MssMtuClampingLayer";
    private static final int DEFAULT_MSS = 1360;
    private static final int DEFAULT_MTU = 1400;

    private final Context context;
    private final CarrierBypassEngine.RootExecutor rootExecutor;
    private final boolean isRoot;
    private int mssValue = DEFAULT_MSS;
    private int mtuValue = DEFAULT_MTU;
    private LayerState state = LayerState.DISABLED;

    public MssMtuClampingLayer(Context context, CarrierBypassEngine.RootExecutor rootExecutor) {
        this.context = context;
        this.rootExecutor = rootExecutor;
        this.isRoot = isRootAvailable();
    }

    @Override
    public Type getType() {
        return Type.MSS_MTU_CLAMPING;
    }

    @Override
    public String getName() {
        return "MSS/MTU Clamping";
    }

    @Override
    public boolean requiresRoot() {
        return true;
    }

    @Override
    public LayerState getState() {
        return state;
    }

    public int getMssValue() {
        return mssValue;
    }

    public int getMtuValue() {
        return mtuValue;
    }

    @Override
    public LayerState apply() {
        if (isRoot) {
            boolean ok = rootExecutor != null && rootExecutor.executeRootCommand(
                    "MSS/MTU Clamping (iptables TCPMSS)",
                    () -> {
                        boolean r1 = executeIptables("-A", "POSTROUTING", "-t", "mangle",
                                "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                                "-j", "TCPMSS", "--clamp-mss-to-pmtu");
                        boolean r2 = executeIptables("-A", "POSTROUTING", "-t", "mangle",
                                "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                                "-j", "TCPMSS", "--set-mss", String.valueOf(mssValue));
                        return r1 && r2;
                    }
            );
            state = ok ? LayerState.ACTIVE : LayerState.FAILED;
        } else {
            state = LayerState.DEGRADED;
            Log.w(TAG, "MSS/MTU clamping requires root; degraded mode");
        }
        return state;
    }

    @Override
    public void revert() {
        if (isRoot && rootExecutor != null) {
            rootExecutor.executeRootCommand(
                    "Revert MSS/MTU Clamping (iptables TCPMSS)",
                    () -> {
                        executeIptables("-D", "POSTROUTING", "-t", "mangle",
                                "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                                "-j", "TCPMSS", "--clamp-mss-to-pmtu");
                        executeIptables("-D", "POSTROUTING", "-t", "mangle",
                                "-p", "tcp", "--tcp-flags", "SYN,RST", "SYN",
                                "-j", "TCPMSS", "--set-mss", String.valueOf(mssValue));
                        return true;
                    }
            );
        }
        state = LayerState.DISABLED;
    }

    public void setMssValue(int mss) {
        this.mssValue = mss > 0 ? mss : DEFAULT_MSS;
    }

    public void setMtuValue(int mtu) {
        this.mtuValue = mtu > 0 ? mtu : DEFAULT_MTU;
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
}
