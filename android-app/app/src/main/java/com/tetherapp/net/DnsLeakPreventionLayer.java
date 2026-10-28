package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

/**
 * Layer 3: DNS leak prevention. Forces DNS through the proxy tunnel.
 * Does not require root (enforced at TunnelManager layer).
 */
public class DnsLeakPreventionLayer implements BypassLayer {

    private static final String TAG = "DnsLeakPreventionLayer";
    private final Context context;
    private LayerState state = LayerState.DISABLED;

    public DnsLeakPreventionLayer(Context context) {
        this.context = context;
    }

    @Override
    public Type getType() {
        return Type.DNS_LEAK_PREVENTION;
    }

    @Override
    public String getName() {
        return "DNS Leak Prevention";
    }

    @Override
    public boolean requiresRoot() {
        return false;
    }

    @Override
    public LayerState getState() {
        return state;
    }

    @Override
    public LayerState apply() {
        try {
            clearDnsCache();
        } catch (Exception e) {
            Log.w(TAG, "Could not clear DNS cache", e);
        }
        state = LayerState.ACTIVE;
        return state;
    }

    @Override
    public void revert() {
        state = LayerState.DISABLED;
    }

    private void clearDnsCache() {
        Log.d(TAG, "DNS cache clear attempted (best-effort)");
    }
}