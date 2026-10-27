package com.tetherapp.net;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * Detects and manages network interfaces relevant to tethering.
 * Provides interface details, IP addresses (IPv4/IPv6), and capability info.
 */
public class NetworkInterfaceManager {
    private static final String TAG = "NetworkInterfaceManager";

    private final Context context;
    private final ConnectivityManager connectivityManager;

    public NetworkInterfaceManager(Context context) {
        this.context = context;
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    public List<InterfaceInfo> listAllInterfaces() {
        List<InterfaceInfo> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return result;
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp()) {
                    continue;
                }
                InterfaceInfo info = new InterfaceInfo();
                info.name = ni.getName();
                info.displayName = ni.getDisplayName();
                info.mtu = ni.getMTU();
                info.isLoopback = ni.isLoopback();
                info.isPointToPoint = ni.isPointToPoint();
                info.isVirtual = ni.isVirtual();

                try {
                    byte[] hw = ni.getHardwareAddress();
                    if (hw != null) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < hw.length; i++) {
                            sb.append(String.format("%02X%s", hw[i], (i < hw.length - 1) ? ":" : ""));
                        }
                        info.macAddress = sb.toString();
                    }
                } catch (SocketException ignored) {
                }

                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        info.ipv4Address = addr.getHostAddress();
                    } else if (addr instanceof Inet6Address) {
                        info.ipv6Address = addr.getHostAddress();
                    }
                }
                result.add(info);
            }
        } catch (SocketException e) {
            Log.e(TAG, "Failed to list network interfaces", e);
        }
        return result;
    }

    public InterfaceInfo getInterfaceByName(String name) {
        for (InterfaceInfo info : listAllInterfaces()) {
            if (name.equals(info.name)) {
                return info;
            }
        }
        return null;
    }

    public NetworkCapabilities getActiveNetworkCapabilities() {
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        return network != null ? connectivityManager.getNetworkCapabilities(network) : null;
    }

    public boolean isTetheredNetworkActive() {
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_USB));
    }

    public boolean isVpnActive() {
        if (connectivityManager == null) {
            return false;
        }
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) {
            return false;
        }
        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(network);
        return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
    }

    public LinkProperties getActiveLinkProperties() {
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        return network != null ? connectivityManager.getLinkProperties(network) : null;
    }

    public String getActiveInterfaceName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (connectivityManager != null) {
                Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    return network.toString();
                }
            }
        }
        return null;
    }

    public static class InterfaceInfo {
        public String name;
        public String displayName;
        public String macAddress;
        public String ipv4Address;
        public String ipv6Address;
        public int mtu = 0;
        public boolean isLoopback;
        public boolean isPointToPoint;
        public boolean isVirtual;

        @Override
        public String toString() {
            return name + " (ipv4=" + ipv4Address + ", ipv6=" + ipv6Address + ", mtu=" + mtu + ")";
        }
    }
}
