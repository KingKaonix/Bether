package com.tetherapp.net;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages Bluetooth Dial-Up Networking (DUN) for Bluetooth tethering.
 * Implements {@link TetherInterface} for polymorphic transport handling.
 */
public class BluetoothDunManager implements TetherInterface {
    private static final String TAG = "BluetoothDunManager";

    // Serial Port Profile UUID (commonly used for DUN)
    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;

    private boolean dunActive = false;
    private BluetoothDevice connectedDevice;
    private final List<DunListener> dunListeners = new ArrayList<>();
    private final List<TetherListener> tetherListeners = new ArrayList<>();

    public interface DunListener {
        void onDunStateChanged(boolean active, BluetoothDevice device);
        void onDeviceConnected(BluetoothDevice device);
        void onDeviceDisconnected(BluetoothDevice device);
    }

    public BluetoothDunManager(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    /* ---------- TetherInterface implementation ---------- */

    @Override
    public String getName() {
        return "Bluetooth DUN";
    }

    @Override
    public TetherMode getMode() {
        return TetherMode.BLUETOOTH;
    }

    @Override
    public boolean isTetheringActive() {
        return dunActive;
    }

    @Override
    public boolean startTethering() {
        if (connectedDevice == null) {
            Log.w(TAG, "No Bluetooth device selected for DUN");
            notifyError("No Bluetooth device selected");
            return false;
        }
        return startDun(connectedDevice);
    }

    @Override
    public boolean stopTethering() {
        return stopDun();
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
        stopDun();
    }

    /* ---------- Bluetooth-specific API ---------- */

    public boolean isBluetoothAvailable() {
        return bluetoothAdapter != null;
    }

    public boolean isBluetoothEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public boolean enableBluetooth() {
        return bluetoothAdapter != null && bluetoothAdapter.enable();
    }

    public List<BluetoothDevice> discoverPairedDevices() {
        List<BluetoothDevice> result = new ArrayList<>();
        if (bluetoothAdapter != null) {
            for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                result.add(device);
            }
        }
        return result;
    }

    public boolean startDun(BluetoothDevice device) {
        if (bluetoothAdapter == null || device == null) {
            return false;
        }
        try {
            this.connectedDevice = device;
            boolean connected = bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTED
                    || bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET) == BluetoothProfile.STATE_CONNECTING;
            dunActive = true;
            notifyTetheringStarted();
            notifyDunStateChanged(true, device);
            Log.i(TAG, "Bluetooth DUN started for " + device.getName());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to start Bluetooth DUN", e);
            notifyError(e.getMessage());
            return false;
        }
    }

    public boolean stopDun() {
        boolean wasActive = dunActive;
        dunActive = false;
        BluetoothDevice dev = connectedDevice;
        connectedDevice = null;
        if (wasActive) {
            notifyTetheringStopped();
            notifyDunStateChanged(false, dev);
        }
        Log.i(TAG, "Bluetooth DUN stopped");
        return true;
    }

    public boolean isDunActive() {
        return dunActive;
    }

    public BluetoothDevice getConnectedDevice() {
        return connectedDevice;
    }

    public void setConnectedDevice(BluetoothDevice device) {
        this.connectedDevice = device;
    }

    public void addDunListener(DunListener listener) {
        dunListeners.add(listener);
    }

    public void removeDunListener(DunListener listener) {
        dunListeners.remove(listener);
    }

    public ParcelUuid[] getDeviceUuids(BluetoothDevice device) {
        if (device == null) {
            return new ParcelUuid[0];
        }
        ParcelUuid[] uuids = device.getUuids();
        return uuids != null ? uuids : new ParcelUuid[0];
    }

    public boolean supportsDun(BluetoothDevice device) {
        if (device == null) {
            return false;
        }
        for (ParcelUuid uuid : getDeviceUuids(device)) {
            if (SPP_UUID.equals(uuid.getUuid())) {
                return true;
            }
        }
        return false;
    }

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

    private void notifyDunStateChanged(boolean active, BluetoothDevice device) {
        for (DunListener l : dunListeners) {
            if (active) {
                l.onDunStateChanged(true, device);
            } else {
                l.onDunStateChanged(false, device);
            }
        }
    }
}