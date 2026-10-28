package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Layer 4: OS update domain blocking via /etc/hosts deny-list.
 * Requires root for writing to system hosts file; degraded otherwise.
 */
public class OsUpdateDomainBlockingLayer implements BypassLayer {

    private static final String TAG = "OsUpdateDomainBlockingLayer";
    private final Context context;
    private final boolean isRoot;
    private LayerState state = LayerState.DISABLED;
    private final List<String> blockedDomains = new ArrayList<>();

    public OsUpdateDomainBlockingLayer(Context context) {
        this.context = context;
        this.isRoot = isRootAvailable();
        initializeBlockedDomains();
    }

    @Override
    public Type getType() {
        return Type.OS_UPDATE_DOMAIN_BLOCKING;
    }

    @Override
    public String getName() {
        return "OS Update Domain Blocking";
    }

    @Override
    public boolean requiresRoot() {
        return true;
    }

    @Override
    public LayerState getState() {
        return state;
    }

    public List<String> getBlockedDomains() {
        return new ArrayList<>(blockedDomains);
    }

    public void addBlockedDomain(String domain) {
        if (domain != null && !blockedDomains.contains(domain)) {
            blockedDomains.add(domain);
        }
    }

    @Override
    public LayerState apply() {
        if (isRoot) {
            for (String domain : blockedDomains) {
                addHostsDenyEntry(domain);
            }
            state = LayerState.ACTIVE;
        } else {
            state = LayerState.DEGRADED;
            Log.w(TAG, "OS update domain blocking degraded (no root)");
        }
        return state;
    }

    @Override
    public void revert() {
        state = LayerState.DISABLED;
    }

    private void initializeBlockedDomains() {
        blockedDomains.add("android.clients.google.com");
        blockedDomains.add(".googleapis.com");
        blockedDomains.add("gvt1.com");
        blockedDomains.add("gs.l.google.com");
        blockedDomains.add("connectivitycheck.android.com");
        blockedDomains.add("captive.apple.com");
        blockedDomains.add("mesu.apple.com");
        blockedDomains.add("swscan.apple.com");
        blockedDomains.add("windowsupdate.com");
        blockedDomains.add("update.microsoft.com");
    }

    private void addHostsDenyEntry(String domain) {
        try {
            File hosts = new File("/system/etc/hosts");
            if (!hosts.canWrite()) {
                hosts = new File("/etc/hosts");
            }
            FileWriter fw = new FileWriter(hosts, true);
            fw.write("0.0.0.0 " + domain + "\n");
            fw.close();
            Log.d(TAG, "Blocked OS update domain: " + domain);
        } catch (IOException e) {
            Log.d(TAG, "Could not write hosts entry for " + domain, e);
        }
    }

    private boolean isRootAvailable() {
        try {
            Process su = Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", "id"});
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(su.getInputStream()));
            String line = br.readLine();
            return line != null && line.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}