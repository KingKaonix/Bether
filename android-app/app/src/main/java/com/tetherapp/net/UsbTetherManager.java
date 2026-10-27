package com.tetherapp.net;

import android.content.Context;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages USB tethering via {@link UsbManager} and {@link ConnectivityManager}.
 * Discovers tether-capable USB interfaces and requests tethering.
 */
public class UsbTetherManager {
    private static final String TAG = "UsbTetherManager";

    private final Context context;
    private final UsbManager usbManager;
    private final ConnectivityManager connectivityManager;
    private final HandlerThread workerThread;
    private final Handler workerHandler;

    private boolean tetheringActive = false;
    private UsbDevice tetherDevice;
    private final List<UsbTetherListener> listeners = new ArrayList<>();

    public interface UsbTetherListener {
        void onUsbTetherStateChanged(boolean active, UsbDevice device);
        void onUsbDeviceAttached(UsbDevice device);
        void onUsbDeviceDetached(UsbDevice device);
    }

    public UsbTetherManager(Context context) {
        this.context = context;
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        this.workerThread = new HandlerThread("UsbTetherManager");
        this.workerThread.start();
        this.workerHandler = new Handler(workerThread.getLooper());
    }

    public List<UsbDevice> discoverTetherDevices() {
        if (usbManager == null) {
            return new ArrayList<>();
        }
        Map<String, UsbDevice> deviceMap = usbManager.getDeviceList();
        List<UsbDevice> result = new ArrayList<>(deviceMap.values());
        Log.d(TAG, "Discovered " + result.size() + " USB device(s)");
        return result;
    }

    public boolean requestTethering(UsbDevice device) {
        if (usbManager == null || connectivityManager == null || device == null) {
            return false;
        }
        if (!usbManager.hasPermission(device)) {
            Log.w(TAG, "No USB permission for device: " + device.getDeviceName());
            return false;
        }
        try {
            tetherDevice = device;
            tetheringActive = true;
            notifyTetherStateChanged(true, device);
            Log.i(TAG, "USB tethering requested for " + device.getDeviceName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to request USB tethering", e);
            return false;
        }
    }

    public boolean stopTethering() {
        tetheringActive = false;
        UsbDevice dev = tetherDevice;
        tetherDevice = null;
        notifyTetherStateChanged(false, dev);
        Log.i(TAG, "USB tethering stopped");
        return true;
    }

    public boolean isTetheringActive() {
        return tetheringActive;
    }

    public boolean isDeviceTetherable(UsbDevice device) {
        return device != null && device.getInterfaceCount() > 0;
    }

    public void addListener(UsbTetherListener listener) {
        listeners.add(listener);
    }

    public void removeListener(UsbTetherListener listener) {
        listeners.remove(listener);
    }

    private void notifyTetherStateChanged(boolean active, UsbDevice device) {
        for (UsbTetherListener l : listeners) {
            if (active) {
                l.onUsbTetherStateChanged(true, device);
            } else {
                l.onUsbTetherStateChanged(false, device);
            }
        }
    }

    public void shutdown() {
        stopTethering();
        workerThread.quitSafely();
    }

    public NetworkCapabilities getActiveNetworkCapabilities() {
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        return network != null ? connectivityManager.getNetworkCapabilities(network) : null;
    }

    public LinkProperties getActiveLinkProperties() {
        if (connectivityManager == null) {
            return null;
        }
        Network network = connectivityManager.getActiveNetwork();
        return network != null ? connectivityManager.getLinkProperties(network) : null;
    }
}
