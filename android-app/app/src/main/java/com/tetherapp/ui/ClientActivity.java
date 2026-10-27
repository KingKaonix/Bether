package com.tetherapp.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tetherapp.R;
import com.tetherapp.net.CarrierBypassEngine;
import com.tetherapp.net.ConnectionStateMachine;

public class ClientActivity extends AppCompatActivity {
    private static final String TAG = "ClientActivity";

    private EditText etHost;
    private EditText etPort;
    private Button btnConnect;
    private Button btnDisconnect;
    private TextView tvStatus;

    private ConnectionStateMachine connectionStateMachine;
    private CarrierBypassEngine carrierBypassEngine;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_client);

        etHost = findViewById(R.id.et_host);
        etPort = findViewById(R.id.et_port);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        tvStatus = findViewById(R.id.tv_status);

        etHost.setText("127.0.0.1");
        etPort.setText("1080");

        connectionStateMachine = new ConnectionStateMachine(10, 1000L);
        carrierBypassEngine = new CarrierBypassEngine(this);

        connectionStateMachine.addListener((prev, current) -> {
            Log.i(TAG, "Client state: " + prev + " -> " + current);
            runOnUiThread(this::refreshStatus);
        });

        btnConnect.setOnClickListener(v -> {
            String host = etHost.getText().toString().trim();
            int port;
            try {
                port = Integer.parseInt(etPort.getText().toString().trim());
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid port", Toast.LENGTH_SHORT).show();
                return;
            }
            connectionStateMachine.connect();
            Toast.makeText(this, "Connecting to " + host + ":" + port, Toast.LENGTH_SHORT).show();
        });

        btnDisconnect.setOnClickListener(v -> {
            connectionStateMachine.disconnected();
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });

        refreshStatus();
    }

    private void refreshStatus() {
        String status = connectionStateMachine.isConnected() ? "CONNECTED"
                : (connectionStateMachine.isConnecting() ? "CONNECTING..." : "DISCONNECTED");
        tvStatus.setText("Client: " + status + "\nBypass layers: " + carrierBypassEngine.activeLayerCount() + "/6");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectionStateMachine.shutdown();
    }
}
