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
 * Implements {@link TetherInterface} for polymorphic transport handling.
 */
public class UsbTetherManager implements TetherInterface {
    private static final String TAG = "UsbTetherManager";

    private final Context context;
    private final UsbManager usbManager;
    private final ConnectivityManager connectivityManager;
    private final HandlerThread workerThread;
    private final Handler workerHandler;

    private boolean tetheringActive = false;
    private UsbDevice tetherDevice;
    private final List<UsbTetherListener> usbListeners = new ArrayList<>();
    private final List<TetherListener> tetherListeners = new ArrayList<>();

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

    /* ---------- TetherInterface implementation ---------- */

    @Override
    public String getName() {
        return "USB";
    }

    @Override
    public TetherMode getMode() {
        return TetherMode.USB;
    }

    @Override
    public boolean isTetheringActive() {
        return tetheringActive;
    }

    @Override
    public boolean startTethering() {
        if (usbManager == null || connectivityManager == null || tetherDevice == null) {
            return false;
        }
        if (!usbManager.hasPermission(tetherDevice)) {
            Log.w(TAG, "No USB permission for device: " + tetherDevice.getDeviceName());
            notifyError("No USB permission for device");
            return false;
        }
        try {
            tetheringActive = true;
            notifyTetheringStarted();
            notifyUsbTetherStateChanged(true, tetherDevice);
            Log.i(TAG, "USB tethering requested for " + tetherDevice.getDeviceName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to request USB tethering", e);
            notifyError(e.getMessage());
            return false;
        }
    }

    @Override
    public boolean stopTethering() {
        boolean wasActive = tetheringActive;
        tetheringActive = false;
        UsbDevice dev = tetherDevice;
        tetherDevice = null;
        if (wasActive) {
            notifyTetheringStopped();
            notifyUsbTetherStateChanged(false, dev);
        }
        Log.i(TAG, "USB tethering stopped");
        return true;
    }

    @Override
    public void addListener(TetherListener listener) {
        tetherListeners.add(listener);
    }

    @Override
    public void removeListener(TetherListener listener) {
        tetherListeners.remove(listener);
    }

    @Override
    public void shutdown() {
        stopTethering();
        workerThread.quitSafely();
    }

    /* ---------- USB-specific API ---------- */

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
        this.tetherDevice = device;
        return startTethering();
    }

    public boolean isDeviceTetherable(UsbDevice device) {
        return device != null && device.getInterfaceCount() > 0;
    }

    public void addUsbListener(UsbTetherListener listener) {
        usbListeners.add(listener);
    }

    public void removeUsbListener(UsbTetherListener listener) {
        usbListeners.remove(listener);
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

    /* ---------- Notification helpers ---------- */

    private void notifyTetheringStarted() {
        for (TetherListener l : tetherListeners) {
            l.onTetheringStarted(getName());
        }
    }

    private void notifyTetheringStopped() {
        for (TetherListener l : tetherListeners) {
            l.onTetheringStopped(getName());
        }
    }

    private void notifyError(String message) {
        for (TetherListener l : tetherListeners) {
            l.onError(getName(), message);
        }
    }

    private void notifyUsbTetherStateChanged(boolean active, UsbDevice device) {
        for (UsbTetherListener l : usbListeners) {
            if (active) {
                l.onUsbTetherStateChanged(true, device);
            } else {
                l.onUsbTetherStateChanged(false, device);
            }
        }
    }
}