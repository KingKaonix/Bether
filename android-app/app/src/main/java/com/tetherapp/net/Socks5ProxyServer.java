package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SOCKS5 proxy server with TCP CONNECT and UDP ASSOCIATE support.
 * Modeled on SocksDroid architecture: accepts client connections, performs
 * SOCKS5 handshake/auth, then relays TCP or forwards UDP via badvpn-udpgw.
 */
public class Socks5ProxyServer {
    private static final String TAG = "Socks5ProxyServer";

    // SOCKS5 protocol constants
    private static final byte SOCKS5_VERSION = 0x05;
    private static final byte CMD_CONNECT = 0x01;
    private static final byte CMD_UDP_ASSOCIATE = 0x03;
    private static final byte ATYP_IPV4 = 0x01;
    private static final byte ATYP_DOMAIN = 0x03;
    private static final byte ATYP_IPV6 = 0x04;
    private static final byte REP_SUCCESS = 0x00;
    private static final byte REP_GENERAL_FAILURE = 0x01;
    private static final byte REP_COMMAND_NOT_SUPPORTED = 0x07;
    private static final byte REP_ADDRESS_TYPE_NOT_SUPPORTED = 0x08;

    private final Context context;
    private final int listenPort;
    private final boolean udpEnabled;

    private ServerSocket serverSocket;
    private DatagramChannel udpChannel;
    private Selector selector;
    private ExecutorService executor;
    private Thread acceptThread;
    private Thread udpThread;

    private volatile boolean running = false;

    public interface ProxyListener {
        void onProxyStarted(int port);
        void onProxyStopped();
        void onClientConnected(String clientAddress);
        void onClientDisconnected(String clientAddress);
        void onTcpRelay(String remoteHost, int remotePort, long bytes);
        void onUdpRelay(String remoteHost, int remotePort, long bytes);
        void onError(String message);
    }

    private final java.util.List<ProxyListener> listeners = new java.util.ArrayList<>();

    public Socks5ProxyServer(Context context, int listenPort, boolean udpEnabled) {
        this.context = context;
        this.listenPort = listenPort;
        this.udpEnabled = udpEnabled;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "socks5-relay");
            t.setDaemon(true);
            return t;
        });
    }

    public boolean start() {
        if (running) {
            return true;
        }
        try {
            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(listenPort));
            running = true;

            acceptThread = new Thread(this::acceptLoop, "socks5-accept");
            acceptThread.setDaemon(true);
            acceptThread.start();

            if (udpEnabled) {
                startUdp();
            }

            Log.i(TAG, "SOCKS5 proxy started on port " + listenPort + " (UDP=" + udpEnabled + ")");
            for (ProxyListener l : listeners) {
                l.onProxyStarted(listenPort);
            }
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to start SOCKS5 proxy", e);
            stop();
            for (ProxyListener l : listeners) {
                l.onError("Failed to start proxy: " + e.getMessage());
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
            if (serverSocket != null) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (IOException ignored) {
        }
        try {
            if (selector != null) {
                selector.close();
                selector = null;
            }
        } catch (IOException ignored) {
        }
        try {
            if (udpChannel != null) {
                udpChannel.close();
                udpChannel = null;
            }
        } catch (IOException ignored) {
        }
        if (executor != null) {
            executor.shutdownNow();
        }
        Log.i(TAG, "SOCKS5 proxy stopped");
        for (ProxyListener l : listeners) {
            l.onProxyStopped();
        }
        return true;
    }

    public boolean isRunning() {
        return running;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void addListener(ProxyListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ProxyListener listener) {
        listeners.remove(listener);
    }

    private void acceptLoop() {
        while (running && serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                if (!running) {
                    client.close();
                    break;
                }
                String clientAddr = client.getInetAddress().getHostAddress();
                Log.d(TAG, "Client connected: " + clientAddr);
                for (ProxyListener l : listeners) {
                    l.onClientConnected(clientAddr);
                }
                executor.execute(() -> handleClient(client));
            } catch (IOException e) {
                if (running) {
                    Log.e(TAG, "Accept error", e);
                }
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket socket = client) {
            socket.setSoTimeout(10000);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            // SOCKS5 greeting: VER, NMETHODS, METHODS
            if (!readFull(in, 2)) {
                return;
            }
            if (in.read() != SOCKS5_VERSION) {
                sendReply(out, REP_GENERAL_FAILURE, null, 0);
                return;
            }
            int nMethods = in.read();
            byte[] methods = new byte[nMethods];
            if (!readFull(in, nMethods)) {
                return;
            }
            boolean supportsNoAuth = false;
            for (byte b : methods) {
                if (b == 0x00) {
                    supportsNoAuth = true;
                    break;
                }
            }
            if (!supportsNoAuth) {
                out.write(new byte[]{SOCKS5_VERSION, 0xFF});
                out.flush();
                return;
            }
            out.write(new byte[]{SOCKS5_VERSION, 0x00});
            out.flush();

            // Request: VER, CMD, RSV, ATYP, ADDR, PORT
            if (!readFull(in, 4)) {
                return;
            }
            if (in.read() != SOCKS5_VERSION) {
                sendReply(out, REP_GENERAL_FAILURE, null, 0);
                return;
            }
            int cmd = in.read();
            in.read(); // RSV
            int atyp = in.read();

            InetSocketAddress remoteAddr = null;
            switch (atyp) {
                case ATYP_IPV4:
                    if (!readFull(in, 4)) return;
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(in.readNBytes(4), 0, ipv4, 0, 4);
                    remoteAddr = parseAddress(ipv4, readPort(in));
                    break;
                case ATYP_IPV6:
                    if (!readFull(in, 16)) return;
                    byte[] ipv6 = new byte[16];
                    System.arraycopy(in.readNBytes(16), 0, ipv6, 0, 16);
                    remoteAddr = parseAddress(ipv6, readPort(in));
                    break;
                case ATYP_DOMAIN:
                    int domainLen = in.read();
                    if (domainLen < 0 || !readFull(in, domainLen)) return;
                    byte[] domainBytes = new byte[domainLen];
                    System.arraycopy(in.readNBytes(domainLen), 0, domainBytes, 0, domainLen);
                    String domain = new String(domainBytes, "UTF-8");
                    int port = readPort(in);
                    remoteAddr = new InetSocketAddress(domain, port);
                    break;
                default:
                    sendReply(out, REP_ADDRESS_TYPE_NOT_SUPPORTED, null, 0);
                    return;
            }

            if (cmd == CMD_CONNECT) {
                handleTcpConnect(socket, out, remoteAddr);
            } else if (cmd == CMD_UDP_ASSOCIATE) {
                handleUdpAssociate(out);
            } else {
                sendReply(out, REP_COMMAND_NOT_SUPPORTED, null, 0);
            }
        } catch (IOException e) {
            Log.e(TAG, "Client handler error", e);
        } finally {
            String clientAddr = client.getInetAddress().getHostAddress();
            Log.d(TAG, "Client disconnected: " + clientAddr);
            for (ProxyListener l : listeners) {
                l.onClientDisconnected(clientAddr);
            }
        }
    }

    private int readPort(InputStream in) throws IOException {
        byte[] b = new byte[2];
        if (!readFull(in, 2)) {
            return -1;
        }
        return ((b[0] & 0xFF) << 8) | (b[1] & 0xFF);
    }

    private InetSocketAddress parseAddress(byte[] ip, int port) throws IOException {
        InetAddress addr = InetAddress.getByAddress(ip);
        return new InetSocketAddress(addr, port);
    }

    private boolean readFull(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int total = 0;
        while (total < n) {
            int read = in.read(buf, total, n - total);
            if (read < 0) {
                return false;
            }
            total += read;
        }
        return true;
    }

    private void sendReply(OutputStream out, byte rep, SocketAddress bindAddress, int bindPort) throws IOException {
        byte[] reply = new byte[]{SOCKS5_VERSION, rep, 0x00, ATYP_IPV4, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        if (bindAddress instanceof InetSocketAddress) {
            InetSocketAddress isa = (InetSocketAddress) bindAddress;
            InetAddress addr = isa.getAddress();
            if (addr instanceof java.net.Inet4Address) {
                byte[] ip = addr.getAddress();
                reply[3] = ATYP_IPV4;
                System.arraycopy(ip, 0, reply, 4, 4);
                reply[8] = (byte) (isa.getPort() >>> 8);
                reply[9] = (byte) isa.getPort();
            } else {
                byte[] ip = addr.getAddress();
                reply[3] = ATYP_IPV6;
                System.arraycopy(ip, 0, reply, 4, 16);
                reply[20] = (byte) (isa.getPort() >>> 8);
                reply[21] = (byte) isa.getPort();
            }
        }
        out.write(reply);
        out.flush();
    }

    private void handleTcpConnect(Socket client, OutputStream out, InetSocketAddress remoteAddr) throws IOException {
        Socket remote = new Socket();
        remote.connect(remoteAddr, 10000);
        InetSocketAddress localBind = new InetSocketAddress(remote.getLocalAddress(), remote.getLocalPort());
        sendReply(out, REP_SUCCESS, localBind, localBind.getPort());

        Log.i(TAG, "TCP relay established: " + remoteAddr.getHostName() + ":" + remoteAddr.getPort());
        for (ProxyListener l : listeners) {
            l.onTcpRelay(remoteAddr.getHostName(), remoteAddr.getPort(), 0);
        }

        relayTcp(client, remote, remoteAddr);
    }

    private void relayTcp(Socket client, Socket remote, InetSocketAddress remoteAddr) {
        try {
            byte[] buf = new byte[8192];
            long totalBytes = 0;
            while (running) {
                int read = client.getInputStream().read(buf);
                if (read < 0) {
                    break;
                }
                remote.getOutputStream().write(buf, 0, read);
                remote.getOutputStream().flush();
                totalBytes += read;

                int readBack = remote.getInputStream().read(buf);
                if (readBack < 0) {
                    break;
                }
                client.getOutputStream().write(buf, 0, readBack);
                client.getOutputStream().flush();
                totalBytes += readBack;
            }
            if (totalBytes > 0) {
                for (ProxyListener l : listeners) {
                    l.onTcpRelay(remoteAddr.getHostName(), remoteAddr.getPort(), totalBytes);
                }
            }
        } catch (IOException ignored) {
        } finally {
            try {
                client.close();
            } catch (IOException ignored) {
            }
            try {
                remote.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void handleUdpAssociate(OutputStream out) throws IOException {
        if (!udpEnabled || udpChannel == null) {
            sendReply(out, REP_COMMAND_NOT_SUPPORTED, null, 0);
            return;
        }
        SocketAddress bindAddr = udpChannel.socket().getLocalSocketAddress();
        sendReply(out, REP_SUCCESS, bindAddr, ((InetSocketAddress) bindAddr).getPort());
        Log.i(TAG, "UDP ASSOCIATE granted on " + bindAddr);
    }

    private void startUdp() throws IOException {
        selector = Selector.open();
        udpChannel = DatagramChannel.open();
        udpChannel.configureBlocking(false);
        udpChannel.bind(new InetSocketAddress(listenPort));
        udpChannel.register(selector, SelectionKey.OP_READ);

        udpThread = new Thread(this::udpLoop, "socks5-udp");
        udpThread.setDaemon(true);
        udpThread.start();
    }

    private void udpLoop() {
        try {
            while (running && selector != null) {
                if (selector.select(1000) == 0) {
                    continue;
                }
                Iterator<SelectionKey> keys = selector.selectedKeys().iterator();
                while (keys.hasNext()) {
                    SelectionKey key = keys.next();
                    keys.remove();
                    if (!key.isValid()) {
                        continue;
                    }
                    if (key.isReadable() && key.channel() instanceof DatagramChannel) {
                        DatagramChannel ch = (DatagramChannel) key.channel();
                        ByteBuffer buf = ByteBuffer.allocate(65535);
                        SocketAddress clientAddr = ch.receive(buf);
                        if (clientAddr == null) {
                            continue;
                        }
                        int len = buf.position();
                        if (len > 0) {
                            buf.flip();
                            byte[] data = new byte[len];
                            buf.get(data);
                            // Forward UDP payload to destination (parsed as SOCKS5 UDP header)
                            forwardUdpPacket(data, clientAddr);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                Log.e(TAG, "UDP loop error", e);
            }
        }
    }

    private void forwardUdpPacket(byte[] data, SocketAddress clientAddr) {
        // Minimal SOCKS5 UDP header parse: RSV(2), FRAG(1), ATYP, ADDR, PORT, DATA
        if (data.length < 7) {
            return;
        }
        int pos = 3; // skip RSV + FRAG
        int atyp = data[pos++] & 0xFF;
        InetSocketAddress remoteAddr = null;
        try {
            switch (atyp) {
                case ATYP_IPV4:
                    byte[] ipv4 = new byte[4];
                    System.arraycopy(data, pos, ipv4, 0, 4);
                    pos += 4;
                    int portV4 = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
                    pos += 2;
                    remoteAddr = new InetSocketAddress(InetAddress.getByAddress(ipv4), portV4);
                    break;
                case ATYP_IPV6:
                    byte[] ipv6 = new byte[16];
                    System.arraycopy(data, pos, ipv6, 0, 16);
                    pos += 16;
                    int portV6 = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
                    pos += 2;
                    remoteAddr = new InetSocketAddress(InetAddress.getByAddress(ipv6), portV6);
                    break;
                case ATYP_DOMAIN:
                    int domainLen = data[pos++] & 0xFF;
                    String domain = new String(data, pos, domainLen, "UTF-8");
                    pos += domainLen;
                    int portD = ((data[pos] & 0xFF) << 8) | (data[pos + 1] & 0xFF);
                    remoteAddr = new InetSocketAddress(domain, portD);
                    break;
                default:
                    return;
            }
            byte[] payload = new byte[data.length - pos];
            System.arraycopy(data, pos, payload, 0, payload.length);

            executor.execute(() -> {
                try {
                    if (udpChannel != null && remoteAddr != null) {
                        udpChannel.send(ByteBuffer.wrap(payload), remoteAddr);
                        for (ProxyListener l : listeners) {
                            l.onUdpRelay(remoteAddr.getHostName(), remoteAddr.getPort(), payload.length);
                        }
                    }
                } catch (IOException e) {
                    Log.e(TAG, "UDP forward error", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse UDP SOCKS5 packet", e);
        }
    }
}
