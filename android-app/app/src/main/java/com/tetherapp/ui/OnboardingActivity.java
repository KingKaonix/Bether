package com.tetherapp.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.tetherapp.R;

public class OnboardingActivity extends AppCompatActivity implements View.OnClickListener {
    private Button btnHost;
    private Button btnClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        btnHost = findViewById(R.id.btn_host);
        btnClient = findViewById(R.id.btn_client);
        btnHost.setOnClickListener(this);
        btnClient.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.btn_host) {
            startActivity(new Intent(this, HostActivity.class));
        } else if (v.getId() == R.id.btn_client) {
            startActivity(new Intent(this, ClientActivity.class));
        }
    }
}
