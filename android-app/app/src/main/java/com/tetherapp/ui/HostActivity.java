package com.tetherapp.ui;

import android.os.Bundle;
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
import com.tetherapp.net.NetworkInterfaceManager;
import com.tetherapp.net.Socks5ProxyServer;
import com.tetherapp.net.TunnelManager;
import com.tetherapp.net.UsbTetherManager;
import com.tetherapp.net.WifiHotspotManager;

public class HostActivity extends AppCompatActivity {
    private static final String TAG = "HostActivity";

    private UsbTetherManager usbTetherManager;
    private WifiHotspotManager wifiHotspotManager;
    private BluetoothDunManager bluetoothDunManager;
    private NetworkInterfaceManager networkInterfaceManager;
    private Socks5ProxyServer socks5ProxyServer;
    private CarrierBypassEngine carrierBypassEngine;
    private ConnectionStateMachine connectionStateMachine;
    private TunnelManager tunnelManager;

    private Switch switchUsb;
    private Switch switchWifi;
    private Switch switchBt;
    private Switch switchProxy;
    private Switch switchBypass;
    private TextView tvStatus;
    private TextView tvBypassStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        initManagers();
        bindViews();
        setupListeners();
        refreshStatus();
    }

    private void initManagers() {
        usbTetherManager = new UsbTetherManager(this);
        wifiHotspotManager = new WifiHotspotManager(this);
        bluetoothDunManager = new BluetoothDunManager(this);
        networkInterfaceManager = new NetworkInterfaceManager(this);
        socks5ProxyServer = new Socks5ProxyServer(this, 1080, true);
        carrierBypassEngine = new CarrierBypassEngine(this);
        connectionStateMachine = new ConnectionStateMachine(10, 1000L);

        connectionStateMachine.addListener((prev, current) -> {
            Log.i(TAG, "Connection state: " + prev + " -> " + current);
            runOnUiThread(this::refreshStatus);
        });

        carrierBypassEngine.addListener((layer, state) -> {
            Log.d(TAG, "Bypass layer " + layer + " -> " + state);
            runOnUiThread(this::refreshBypassStatus);
        });

        socks5ProxyServer.addListener(new Socks5ProxyServer.ProxyListener() {
            @Override public void onProxyStarted(int port) {
                Log.i(TAG, "SOCKS5 started on " + port);
                runOnUiThread(HostActivity.this::refreshStatus);
            }
            @Override public void onProxyStopped() {
                Log.i(TAG, "SOCKS5 stopped");
                runOnUiThread(HostActivity.this::refreshStatus);
            }
            @Override public void onClientConnected(String clientAddress) {
                Log.d(TAG, "Client connected: " + clientAddress);
            }
            @Override public void onClientDisconnected(String clientAddress) {
                Log.d(TAG, "Client disconnected: " + clientAddress);
            }
            @Override public void onTcpRelay(String remoteHost, int remotePort, long bytes) {
                Log.d(TAG, "TCP relay " + remoteHost + ":" + remotePort + " " + bytes + "B");
            }
            @Override public void onUdpRelay(String remoteHost, int remotePort, long bytes) {
                Log.d(TAG, "UDP relay " + remoteHost + ":" + remotePort + " " + bytes + "B");
            }
            @Override public void onError(String message) {
                Log.e(TAG, "Proxy error: " + message);
                runOnUiThread(() -> Toast.makeText(HostActivity.this, message, Toast.LENGTH_SHORT).show());
            }
        });
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
                wifiHotspotManager.startHotspot();
            } else {
                wifiHotspotManager.stopHotspot();
            }
        });

        switchBt.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!bluetoothDunManager.isBluetoothEnabled()) {
                    bluetoothDunManager.enableBluetooth();
                }
            } else {
                bluetoothDunManager.stopDun();
            }
        });

        switchProxy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                socks5ProxyServer.start();
            } else {
                socks5ProxyServer.stop();
            }
        });

        switchBypass.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                applyCarrierBypass();
            } else {
                carrierBypassEngine.disableAll();
            }
            refreshBypassStatus();
        });
    }

    private void applyCarrierBypass() {
        carrierBypassEngine.applyTtlNormalization(CarrierBypassEngine.DEFAULT_TTL);
        carrierBypassEngine.applyIpv6Blocking();
        carrierBypassEngine.applyDnsLeakPrevention();
        carrierBypassEngine.applyOsUpdateDomainBlocking();
        carrierBypassEngine.applyMssMtuClamping(CarrierBypassEngine.DEFAULT_MSS, 1400);
        carrierBypassEngine.applyTrafficShaping();
        carrierBypassEngine.dumpStatus();
    }

    private void refreshStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append("USB: ").append(usbTetherManager.isTetheringActive() ? "ON" : "off").append("\n");
        sb.append("WiFi: ").append(wifiHotspotManager.isHotspotActive() ? "ON" : "off").append("\n");
        sb.append("Proxy: ").append(socks5ProxyServer.isRunning() ? "ON (" + socks5ProxyServer.getListenPort() + ")" : "off");
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

    private android.hardware.usb.UsbDevice pickFirstUsbDevice() {
        var devices = usbTetherManager.discoverTetherDevices();
        return devices.isEmpty() ? null : devices.get(0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        socks5ProxyServer.stop();
        wifiHotspotManager.stopHotspot();
        usbTetherManager.stopTethering();
        bluetoothDunManager.stopDun();
        carrierBypassEngine.disableAll();
        connectionStateMachine.shutdown();
        usbTetherManager.shutdown();
    }
}
