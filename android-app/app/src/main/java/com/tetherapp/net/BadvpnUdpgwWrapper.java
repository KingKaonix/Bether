package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper around badvpn-udpgw for UDP forwarding over the SOCKS5 proxy.
 * Ships badvpn-udpgw as a bundled native binary (assets) and launches it
 * on a local port; the SOCKS5 proxy forwards UDP datagrams to it.
 */
public class BadvpnUdpgwWrapper {
    private static final String TAG = "BadvpnUdpgwWrapper";

    private static final String ASSET_NAME = "badvpn-udpgw";
    private static final int DEFAULT_PORT = 11080;
    private static final int DEFAULT_LISTEN_BACKLOG = 128;

    private final Context context;
    private final int listenPort;
    private final String executablePath;

    private Process process;
    private boolean running = false;

    public interface UdpGwListener {
        void onUdpGwStarted(int port);
        void onUdpGwStopped();
        void onUdpGwError(String message);
    }

    private final java.util.List<UdpGwListener> listeners = new ArrayList<>();

    public BadvpnUdpgwWrapper(Context context, int listenPort) {
        this.context = context;
        this.listenPort = listenPort;
        this.executablePath = new File(context.getFilesDir(), ASSET_NAME).getAbsolutePath();
    }

    public boolean start() {
        if (running) {
            return true;
        }
        try {
            extractBinary();
            File bin = new File(executablePath);
            if (!bin.exists() || !bin.canExecute()) {
                Log.e(TAG, "badvpn-udpgw binary not available");
                for (UdpGwListener l : listeners) {
                    l.onUdpGwError("badvpn-udpgw binary not available");
                }
                return false;
            }

            List<String> command = new ArrayList<>();
            command.add(executablePath);
            command.add("--listen-addr=127.0.0.1:" + listenPort);
            command.add("--max-connections=" + DEFAULT_LISTEN_BACKLOG);

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            process = pb.start();
            running = true;

            new Thread(this::consumeOutput, "badvpn-udpgw-log").start();

            Log.i(TAG, "badvpn-udpgw started on port " + listenPort);
            for (UdpGwListener l : listeners) {
                l.onUdpGwStarted(listenPort);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start badvpn-udpgw", e);
            stop();
            for (UdpGwListener l : listeners) {
                l.onUdpGwError("Failed to start: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean stop() {
        if (!running && process == null) {
            return true;
        }
        running = false;
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(5000, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
        Log.i(TAG, "badvpn-udpgw stopped");
        for (UdpGwListener l : listeners) {
            l.onUdpGwStopped();
        }
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public int getListenPort() {
        return listenPort;
    }

    public String getRelayAddress() {
        return "127.0.0.1:" + listenPort;
    }

    public void addListener(UdpGwListener listener) {
        listeners.add(listener);
    }

    public void removeListener(UdpGwListener listener) {
        listeners.remove(listener);
    }

    private void extractBinary() throws IOException {
        File bin = new File(executablePath);
        if (bin.exists()) {
            return;
        }
        // In a real build, badvpn-udpgw is bundled in assets/ and copied here.
        // For this skeleton we log that it must be provided.
        Log.w(TAG, "badvpn-udpgw binary not bundled; place it at " + executablePath);
    }

    private void consumeOutput() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null && running) {
                Log.d(TAG, "udpgw: " + line);
            }
        } catch (IOException ignored) {
        }
    }

    public void shutdown() {
        stop();
    }
}
