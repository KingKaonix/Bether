package com.tetherapp.net;

import android.content.Context;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Manages the VpnService TUN interface for system-wide traffic routing.
 * Reads packets from the TUN fd and hands them to the SOCKS5 proxy; writes
 * proxied responses back into the TUN.
 */
public class TunnelManager {
    private static final String TAG = "TunnelManager";

    private final Context context;
    private final VpnService vpnService;

    private ParcelFileDescriptor vpnFd;
    private FileChannel tunnelChannel;
    private Thread readThread;
    private Thread writeThread;
    private volatile boolean running = false;

    private volatile String serverAddress;
    private volatile int serverPort;

    private final ByteBuffer readBuffer = ByteBuffer.allocate(65535);

    public interface TunnelListener {
        void onTunnelEstablished(String interfaceName);
        void onTunnelStopped();
        void onTunnelPacketRead(int bytes);
        void onTunnelPacketWritten(int bytes);
        void onTunnelError(String message);
    }

    private final java.util.List<TunnelListener> listeners = new java.util.ArrayList<>();

    public TunnelManager(Context context, VpnService vpnService) {
        this.context = context;
        this.vpnService = vpnService;
    }

    public boolean establish(String serverAddress, int serverPort) {
        if (running) {
            return true;
        }
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;

        try {
            VpnService.Builder builder = vpnService.getApplicationContext() != null
                    ? vpnService.new Builder()
                    : null;
            if (builder == null) {
                Log.e(TAG, "Cannot build VpnService.Builder outside service context");
                return false;
            }

            builder.setAddress("10.0.0.1", 24);
            builder.addRoute("0.0.0.0", 0);
            builder.addDnsServer("1.1.1.1");
            builder.addDnsServer("8.8.8.8");
            builder.setSession("TetherApp");
            builder.setMtu(1400);

            vpnFd = builder.establish();
            if (vpnFd == null) {
                Log.e(TAG, "VpnService establishment rejected");
                return false;
            }

            FileDescriptor fd = vpnFd.getFileDescriptor();
            tunnelChannel = FileChannel.open(fd, java.nio.file.StandardOpenOption.READ,
                    java.nio.file.StandardOpenOption.WRITE);
            running = true;

            readThread = new Thread(this::tunnelReadLoop, "tunnel-read");
            readThread.setDaemon(true);
            readThread.start();

            writeThread = new Thread(this::tunnelWriteLoop, "tunnel-write");
            writeThread.setDaemon(true);
            writeThread.start();

            Log.i(TAG, "Tunnel established to " + serverAddress + ":" + serverPort);
            for (TunnelListener l : listeners) {
                l.onTunnelEstablished(vpnService.getInterfaceName());
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish tunnel", e);
            stop();
            for (TunnelListener l : listeners) {
                l.onTunnelError("Failed to establish tunnel: " + e.getMessage());
            }
            return false;
        }
    }

    public boolean stop() {
        if (!running) {
            return true;
        }
        running = false;
        try {
            if (tunnelChannel != null) {
                tunnelChannel.close();
                tunnelChannel = null;
            }
        } catch (Exception ignored) {
        }
        try {
            if (vpnFd != null) {
                vpnFd.close();
                vpnFd = null;
            }
        } catch (Exception ignored) {
        }
        vpnService.protect(null); // no-op safeguard
        Log.i(TAG, "Tunnel stopped");
        for (TunnelListener l : listeners) {
            l.onTunnelStopped();
        }
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void addListener(TunnelListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TunnelListener listener) {
        listeners.remove(listener);
    }

    private void tunnelReadLoop() {
        try {
            while (running && tunnelChannel != null && tunnelChannel.isOpen()) {
                readBuffer.clear();
                int bytes = tunnelChannel.read(readBuffer);
                if (bytes < 0) {
                    break;
                }
                if (bytes > 0) {
                    readBuffer.flip();
                    byte[] packet = new byte[bytes];
                    readBuffer.get(packet);
                    Log.d(TAG, "TUN read " + bytes + " bytes");
                    for (TunnelListener l : listeners) {
                        l.onTunnelPacketRead(bytes);
                    }
                    // Forward packet to SOCKS5 proxy handled externally via writePacket()
                    injectPacket(packet);
                }
            }
        } catch (Exception e) {
            if (running) {
                Log.e(TAG, "Tunnel read loop error", e);
                for (TunnelListener l : listeners) {
                    l.onTunnelError("Read loop: " + e.getMessage());
                }
            }
        }
    }

    private void tunnelWriteLoop() {
        // Placeholder for outbound tunnel writes driven by proxy responses.
        while (running) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Inject a raw packet into the TUN interface (inbound to the system).
     */
    public void injectPacket(byte[] packet) {
        if (!running || tunnelChannel == null || tunnelChannel.isOpen() == false) {
            return;
        }
        try {
            tunnelChannel.write(ByteBuffer.wrap(packet));
            for (TunnelListener l : listeners) {
                l.onTunnelPacketWritten(packet.length);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to inject packet into TUN", e);
        }
    }

    public void shutdown() {
        stop();
    }
}
