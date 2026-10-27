package com.tetherapp.net;

import android.content.Context;

public class CarrierBypassEngine {
    public static final int DEFAULT_TTL = 65;
    public static final int DEFAULT_MSS = 1360;

    private final Context context;

    public CarrierBypassEngine(Context context) {
        this.context = context;
    }

    public boolean applyTtlNormalization(int ttl) {
        return true;
    }

    public boolean applyIpv6Blocking() {
        return true;
    }

    public boolean applyDnsLeakPrevention() {
        return true;
    }

    public boolean applyOsUpdateDomainBlocking() {
        return true;
    }

    public boolean applyMssMtuClamping(int mss) {
        return true;
    }

    public boolean applyTrafficShaping() {
        return true;
    }
}
