package com.tetherapp.net;

import android.content.Context;

public class Socks5Proxy {
    private final Context context;

    public Socks5Proxy(Context context) {
        this.context = context;
    }

    public boolean start(int port) {
        return true;
    }

    public boolean stop() {
        return true;
    }

    public boolean isRunning() {
        return false;
    }
}
