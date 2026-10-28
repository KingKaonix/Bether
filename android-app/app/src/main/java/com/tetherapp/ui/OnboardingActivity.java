package com.tetherapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tetherapp.R;
import com.tetherapp.net.RoleManager;

/**
 * Onboarding activity with device detection flow.
 *
 * 1. Launch app → scan for tethering interfaces
 * 2. Present "Tethering Role" options (Host / Client)
 * 3. Based on selection:
 *    - HOST: activates SOCKS5 proxy + carrier bypass (HostActivity)
 *    - CLIENT: connects to remote SOCKS5 server (ClientActivity)
 */
public class OnboardingActivity extends AppCompatActivity implements View.OnClickListener {
    private static final String TAG = "OnboardingActivity";

    private RoleManager roleManager;
    private RadioGroup roleGroup;
    private RadioButton radioHost;
    private RadioButton radioClient;
    private TextView tvDetection;
    private Button btnContinue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        roleManager = new RoleManager(this);

        roleGroup = findViewById(R.id.role_group);
        radioHost = findViewById(R.id.radio_host);
        radioClient = findViewById(R.id.radio_client);
        tvDetection = findViewById(R.id.tv_detection);
        btnContinue = findViewById(R.id.btn_continue);
        btnContinue.setOnClickListener(this);

        // Scan interfaces and show detection result
        RoleManager.DeviceRole detected = roleManager.detectRole();
        tvDetection.setText("Detected: " + roleManager.getRoleSummary());

        // Pre-select based on detection, but allow override
        if (detected == RoleManager.DeviceRole.HOST) {
            radioHost.setChecked(true);
        } else {
            radioClient.setChecked(true);
        }

        Log.i(TAG, "Onboarding shown; detected role=" + detected);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_continue) {
            int selectedId = roleGroup.getCheckedRadioButtonId();
            if (selectedId == R.id.radio_host) {
                roleManager.setRole(RoleManager.DeviceRole.HOST);
                startActivity(new Intent(this, HostActivity.class));
            } else if (selectedId == R.id.radio_client) {
                roleManager.setRole(RoleManager.DeviceRole.CLIENT);
                startActivity(new Intent(this, ClientActivity.class));
            } else {
                Toast.makeText(this, "Please select a role", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
