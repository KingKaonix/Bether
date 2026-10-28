package com.tetherapp.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tetherapp.R;
import com.tetherapp.net.BluetoothDunManager;
import com.tetherapp.net.CarrierBypassEngine;
import com.tetherapp.net.ConnectionStateMachine;
import com.tetherapp.net.ProxyService;
import com.tetherapp.net.TetherInterface;
import com.tetherapp.net.UsbTetherManager;
import com.tetherapp.net.WifiHotspotManager;

import java.util.ArrayList;
import java.util.List;

public class HostActivity extends AppCompatActivity {
    private static final String TAG = "HostActivity";

    private UsbTetherManager usbTetherManager;
    private WifiHotspotManager wifiHotspotManager;
    private BluetoothDunManager bluetoothDunManager;
    private CarrierBypassEngine carrierBypassEngine;
    private ConnectionStateMachine connectionStateMachine;

    private ProxyService proxyService;
    private boolean proxyBound = false;

    private final List<TetherInterface> tetherTransports = new ArrayList<>();

    private Switch switchUsb;
    private Switch switchWifi;
    private Switch switchBt;
    private Switch switchProxy;
    private Switch switchBypass;
    private TextView tvStatus;
    private TextView tvBypassStatus;

    private final ServiceConnection proxyServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ProxyService.LocalBinder binder = (ProxyService.LocalBinder) service;
            proxyService = binder.getService();
            proxyBound = true;
            Log.d(TAG, "Bound to ProxyService");
            refreshStatus();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            proxyBound = false;
            proxyService = null;
            Log.d(TAG, "Unbound from ProxyService");
            refreshStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        initManagers();
        bindViews();
        setupListeners();
        bindProxyService();
        refreshStatus();
    }

    private void initManagers() {
        usbTetherManager = new UsbTetherManager(this);
        wifiHotspotManager = new WifiHotspotManager(this);
        bluetoothDunManager = new BluetoothDunManager(this);

        tetherTransports.add(usbTetherManager);
        tetherTransports.add(wifiHotspotManager);
        tetherTransports.add(bluetoothDunManager);

        for (TetherInterface transport : tetherTransports) {
            transport.addListener(new TetherInterface.TetherListener() {
                @Override
                public void onTetheringStarted(String transportName) {
                    Log.i(TAG, "Tethering started: " + transportName);
                    runOnUiThread(HostActivity.this::refreshStatus);
                }

                @Override
                public void onTetheringStopped(String transportName) {
                    Log.i(TAG, "Tethering stopped: " + transportName);
                    runOnUiThread(HostActivity.this::refreshStatus);
                }

                @Override
                public void onError(String transportName, String message) {
                    Log.e(TAG, "Tether error (" + transportName + "): " + message);
                    runOnUiThread(() -> Toast.makeText(
                            HostActivity.this, transportName + ": " + message, Toast.LENGTH_SHORT).show());
                }
            });
        }

        carrierBypassEngine = new CarrierBypassEngine(this);
        carrierBypassEngine.addListener((layer, state) -> {
            Log.d(TAG, "Bypass layer " + layer + " -> " + state);
            runOnUiThread(this::refreshBypassStatus);
        });

        connectionStateMachine = new ConnectionStateMachine(10, 1000L);
        connectionStateMachine.addListener((prev, current) -> {
            Log.i(TAG, "Connection state: " + prev + " -> " + current);
            runOnUiThread(this::refreshStatus);
        });
    }

    private void bindProxyService() {
        Intent intent = new Intent(this, ProxyService.class);
        bindService(intent, proxyServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private void bindViews() {
        switchUsb = findViewById(R.id.switch_usb);
        switchWifi = findViewById(R.id.switch_wifi);
        switchBt = findViewById(R.id.switch_bt);
        switchProxy = findViewById(R.id.switch_proxy);
        switchBypass = findViewById(R.id.switch_bypass);
        tvStatus = findViewById(R.id.tv_status);
        tvBypassStatus = findViewById(R.id.tv_bypass_status);
    }

    private void setupListeners() {
        switchUsb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                usbTetherManager.requestTethering(pickFirstUsbDevice());
            } else {
                usbTetherManager.stopTethering();
            }
        });

        switchWifi.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                wifiHotspotManager.startTethering();
            } else {
                wifiHotspotManager.stopTethering();
            }
        });

        switchBt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!bluetoothDunManager.isBluetoothEnabled()) {
                    bluetoothDunManager.enableBluetooth();
                }
                // TODO: let user pick paired device; default to first paired
                var devices = bluetoothDunManager.discoverPairedDevices();
                if (!devices.isEmpty()) {
                    bluetoothDunManager.setConnectedDevice(devices.get(0));
                }
            } else {
                bluetoothDunManager.stopTethering();
            }
        });

        switchProxy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (proxyBound && proxyService != null) {
                    proxyService.startProxy(ProxyService.DEFAULT_PORT, true);
                } else {
                    Toast.makeText(this, "Proxy service not ready", Toast.LENGTH_SHORT).show();
                }
            } else {
                if (proxyBound && proxyService != null) {
                    proxyService.stopProxy();
                }
            }
            refreshStatus();
        });

        switchBypass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                carrierBypassEngine.applyAll();
            } else {
                carrierBypassEngine.disableAll();
            }
            refreshBypassStatus();
        });
    }

    private android.hardware.usb.UsbDevice pickFirstUsbDevice() {
        var devices = usbTetherManager.discoverTetherDevices();
        return devices.isEmpty() ? null : devices.get(0);
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("USB: ").append(usbTetherManager.isTetheringActive() ? "ON" : "off").append("\n");
        sb.append("WiFi: ").append(wifiHotspotManager.isTetheringActive() ? "ON" : "off").append("\n");
        sb.append("BT: ").append(bluetoothDunManager.isTetheringActive() ? "ON" : "off").append("\n");
        boolean proxyRunning = proxyBound && proxyService != null && proxyService.isProxyRunning();
        sb.append("Proxy: ").append(proxyRunning ? "ON (" + ProxyService.DEFAULT_PORT + ")" : "off");
        tvStatus.setText(sb.toString());
    }

    private void refreshBypassStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("Active layers: ").append(carrierBypassEngine.activeLayerCount()).append("/6\n");
        sb.append("Root: ").append(carrierBypassEngine.isRoot()).append("\n");
        sb.append("TTL: ").append(carrierBypassEngine.getTtlState()).append("\n");
        sb.append("IPv6: ").append(carrierBypassEngine.getIpv6State()).append("\n");
        sb.append("DNS: ").append(carrierBypassEngine.getDnsState()).append("\n");
        sb.append("OS update: ").append(carrierBypassEngine.getOsUpdateState()).append("\n");
        sb.append("MSS: ").append(carrierBypassEngine.getMssState()).append("\n");
        sb.append("Shaping: ").append(carrierBypassEngine.getShapingState());
        tvBypassStatus.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (proxyBound) {
            unbindService(proxyServiceConnection);
            proxyBound = false;
        }
        for (TetherInterface transport : tetherTransports) {
            transport.shutdown();
        }
        carrierBypassEngine.disableAll();
        connectionStateMachine.shutdown();
    }
}