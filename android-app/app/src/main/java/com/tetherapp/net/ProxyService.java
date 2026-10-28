package com.tetherapp.net;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Standalone bound service hosting the SOCKS5 proxy server.
 *
 * Runs independently of any Activity so the proxy stays up while the
 * UI is in the background. HostActivity binds to this service to start/
 * stop the proxy and receive connection events.
 */
public class ProxyService extends Service {

    private static final String TAG = "ProxyService";
    public static final int DEFAULT_PORT = 1080;

    public static final String ACTION_START_PROXY = "com.tetherapp.action.START_PROXY";
    public static final String ACTION_STOP_PROXY = "com.tetherapp.action.STOP_PROXY";
    public static final String EXTRA_PORT = "com.tetherapp.extra.PORT";
    public static final String EXTRA_UDP_ENABLED = "com.tetherapp.extra.UDP_ENABLED";

    private final IBinder binder = new LocalBinder();
    private Socks5ProxyServer proxyServer;
    private int listenPort = DEFAULT_PORT;
    private boolean udpEnabled = true;

    public class LocalBinder extends Binder {
        public ProxyService getService() {
            return ProxyService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_PROXY.equals(action)) {
                int port = intent.getIntExtra(EXTRA_PORT, DEFAULT_PORT);
                boolean udp = intent.getBooleanExtra(EXTRA_UDP_ENABLED, true);
                startProxy(port, udp);
            } else if (ACTION_STOP_PROXY.equals(action)) {
                stopProxy();
            }
        }
        return START_NOT_STICKY;
    }

    /** Start the SOCKS5 proxy (callable directly or via bind). */
    public boolean startProxy(int port, boolean udpEnabled) {
        stopProxy();
        this.listenPort = port;
        this.udpEnabled = udpEnabled;
        proxyServer = new Socks5ProxyServer(this, port, udpEnabled);
        proxyServer.addListener(new Socks5ProxyServer.ProxyListener() {
            @Override public void onProxyStarted(int p) {
                Log.i(TAG, "SOCKS5 proxy started on port " + p);
            }
            @Override public void onProxyStopped() {
                Log.i(TAG, "SOCKS5 proxy stopped");
            }
            @Override public void onClientConnected(String addr) {
                Log.d(TAG, "Client connected: " + addr);
            }
            @Override public void onClientDisconnected(String addr) {
                Log.d(TAG, "Client disconnected: " + addr);
            }
            @Override public void onTcpRelay(String host, int port, long bytes) {
                Log.d(TAG, "TCP relay " + host + ":" + port + " " + bytes + "B");
            }
            @Override public void onUdpRelay(String host, int port, long bytes) {
                Log.d(TAG, "UDP relay " + host + ":" + port + " " + bytes + "B");
            }
            @Override public void onError(String message) {
                Log.e(TAG, "Proxy error: " + message);
            }
        });
        boolean ok = proxyServer.start();
        if (ok) {
            Log.i(TAG, "ProxyService: SOCKS5 proxy started on port " + port + " (UDP=" + udpEnabled + ")");
        }
        return ok;
    }

    /** Stop the SOCKS5 proxy. */
    public boolean stopProxy() {
        if (proxyServer != null) {
            boolean ok = proxyServer.stop();
            proxyServer = null;
            Log.i(TAG, "ProxyService: SOCKS5 proxy stopped");
            return ok;
        }
        return true;
    }

    public boolean isProxyRunning() {
        return proxyServer != null && proxyServer.isRunning();
    }

    public int getListenPort() {
        return listenPort;
    }

    public void addProxyListener(Socks5ProxyServer.ProxyListener listener) {
        if (proxyServer != null) {
            proxyServer.addListener(listener);
        }
    }

    public void removeProxyListener(Socks5ProxyServer.ProxyListener listener) {
        if (proxyServer != null) {
            proxyServer.removeListener(listener);
        }
    }

    @Override
    public void onDestroy() {
        stopProxy();
        super.onDestroy();
    }
}