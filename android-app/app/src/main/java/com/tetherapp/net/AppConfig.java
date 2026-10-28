package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Configuration model for Android tether host.
 * Supports loading and saving to JSON file in app's internal files directory.
 */
public class AppConfig {
    private static final String TAG = "AppConfig";
    private static final String CONFIG_FILE_NAME = "tether_config.json";

    private int proxyPort;
    private String bindAddress;
    private boolean tlsEnabled;
    private boolean authRequired;
    private boolean autoStartProxy;
    private int ttlValue;
    private boolean ipv6BlockingEnabled;
    private boolean dnsLeakPreventionEnabled;
    private boolean mssClampingEnabled;

    public AppConfig() {
        this.proxyPort = 1080;
        this.bindAddress = "127.0.0.1";
        this.tlsEnabled = false;
        this.authRequired = true;
        this.autoStartProxy = false;
        this.ttlValue = 65;
        this.ipv6BlockingEnabled = true;
        this.dnsLeakPreventionEnabled = true;
        this.mssClampingEnabled = true;
    }

    public static AppConfig getDefault() {
        return new AppConfig();
    }

    public static AppConfig load(Context context) {
        File file = new File(context.getFilesDir(), CONFIG_FILE_NAME);
        if (!file.exists()) {
            Log.i(TAG, "No config file found, returning default config.");
            return getDefault();
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] data = new byte[(int) file.length()];
            int read = fis.read(data);
            if (read > 0) {
                String jsonStr = new String(data, 0, read, StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(jsonStr);
                AppConfig config = new AppConfig();
                config.proxyPort = json.optInt("proxyPort", 1080);
                config.bindAddress = json.optString("bindAddress", "127.0.0.1");
                config.tlsEnabled = json.optBoolean("tlsEnabled", false);
                config.authRequired = json.optBoolean("authRequired", true);
                config.autoStartProxy = json.optBoolean("autoStartProxy", false);

                JSONObject bypass = json.optJSONObject("bypassConfig");
                if (bypass != null) {
                    config.ttlValue = bypass.optInt("ttlValue", 65);
                    config.ipv6BlockingEnabled = bypass.optBoolean("ipv6BlockingEnabled", true);
                    config.dnsLeakPreventionEnabled = bypass.optBoolean("dnsLeakPreventionEnabled", true);
                    config.mssClampingEnabled = bypass.optBoolean("mssClampingEnabled", true);
                }
                Log.i(TAG, "Config loaded successfully from " + file.getAbsolutePath());
                return config;
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to load config, falling back to default", e);
        }
        return getDefault();
    }

    public void save(Context context) {
        File file = new File(context.getFilesDir(), CONFIG_FILE_NAME);
        try {
            JSONObject json = new JSONObject();
            json.put("proxyPort", proxyPort);
            json.put("bindAddress", bindAddress);
            json.put("tlsEnabled", tlsEnabled);
            json.put("authRequired", authRequired);
            json.put("autoStartProxy", autoStartProxy);

            JSONObject bypass = new JSONObject();
            bypass.put("ttlValue", ttlValue);
            bypass.put("ipv6BlockingEnabled", ipv6BlockingEnabled);
            bypass.put("dnsLeakPreventionEnabled", dnsLeakPreventionEnabled);
            bypass.put("mssClampingEnabled", mssClampingEnabled);
            json.put("bypassConfig", bypass);

            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.toString(2).getBytes(StandardCharsets.UTF_8));
                fos.flush();
            }
            Log.i(TAG, "Config saved successfully to " + file.getAbsolutePath());
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Failed to save config", e);
        }
    }

    // Getters and Setters
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }

    public String getBindAddress() { return bindAddress; }
    public void setBindAddress(String bindAddress) { this.bindAddress = bindAddress; }

    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }

    public boolean isAuthRequired() { return authRequired; }
    public void setAuthRequired(boolean authRequired) { this.authRequired = authRequired; }

    public boolean isAutoStartProxy() { return autoStartProxy; }
    public void setAutoStartProxy(boolean autoStartProxy) { this.autoStartProxy = autoStartProxy; }

    public int getTtlValue() { return ttlValue; }
    public void setTtlValue(int ttlValue) { this.ttlValue = ttlValue; }

    public boolean isIpv6BlockingEnabled() { return ipv6BlockingEnabled; }
    public void setIpv6BlockingEnabled(boolean ipv6BlockingEnabled) { this.ipv6BlockingEnabled = ipv6BlockingEnabled; }

    public boolean isDnsLeakPreventionEnabled() { return dnsLeakPreventionEnabled; }
    public void setDnsLeakPreventionEnabled(boolean dnsLeakPreventionEnabled) { this.dnsLeakPreventionEnabled = dnsLeakPreventionEnabled; }

    public boolean isMssClampingEnabled() { return mssClampingEnabled; }
    public void setMssClampingEnabled(boolean mssClampingEnabled) { this.mssClampingEnabled = mssClampingEnabled; }
}
