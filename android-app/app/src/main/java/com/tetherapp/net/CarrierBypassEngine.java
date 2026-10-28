package com.tetherapp.net;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 6-layer carrier detection bypass engine.
 *
 * Refactored to compose pluggable {@link BypassLayer} implementations.
 * Each layer is independently applyable/revertable. The engine aggregates
 * per-layer state and notifies listeners of changes.
 *
 * Layers:
 *   1. TTL normalization (set to 65)
 *   2. IPv6 blocking via sysctl
 *   3. DNS leak prevention
 *   4. OS update domain blocking via /etc/hosts
 *   5. MSS/MTU clamping via iptables mangle
 *   6. Optional traffic shaping (placeholder)
 *
 * Layers requiring root run in DEGRADED mode when root is unavailable.
 */
public class CarrierBypassEngine {
    private static final String TAG = "CarrierBypassEngine";

    private final Context context;
    private final boolean isRoot;

    // Pluggable layers
    private final Map<BypassLayer.Type, BypassLayer> layers = new HashMap<>();
    private final List<BypassLayer.Type> layerOrder = new ArrayList<>();

    private final java.util.List<BypassListener> listeners = new java.util.ArrayList<>();

    public CarrierBypassEngine(Context context) {
        this.context = context;
        this.isRoot = isRootAvailable();
        initializeLayers();
        Log.i(TAG, "CarrierBypassEngine initialized (root=" + isRoot + ")");
    }

    private void initializeLayers() {
        addLayer(new TtlNormalizationLayer(context));
        addLayer(new Ipv6BlockingLayer(context));
        addLayer(new DnsLeakPreventionLayer(context));
        addLayer(new OsUpdateDomainBlockingLayer(context));
        addLayer(new MssMtuClampingLayer(context));
        addLayer(new TrafficShapingLayer(context));
    }

    private void addLayer(BypassLayer layer) {
        layers.put(layer.getType(), layer);
        layerOrder.add(layer.getType());
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

    /* ================================================================
     * Layer accessors
     * ================================================================ */

    public BypassLayer getLayer(BypassLayer.Type type) {
        return layers.get(type);
    }

    public List<BypassLayer> getAllLayers() {
        List<BypassLayer> result = new ArrayList<>();
        for (BypassLayer.Type t : layerOrder) {
            result.add(layers.get(t));
        }
        return result;
    }

    /* ================================================================
     * Legacy-compatible apply methods (delegated to layers)
     * ================================================================ */

    public boolean applyTtlNormalization(int ttl) {
        TtlNormalizationLayer layer = (TtlNormalizationLayer) layers.get(BypassLayer.Type.TTL_NORMALIZATION);
        if (layer != null) {
            layer.setTtlValue(ttl);
        }
        return applyLayer(BypassLayer.Type.TTL_NORMALIZATION);
    }

    public boolean applyIpv6Blocking() {
        return applyLayer(BypassLayer.Type.IPV6_BLOCKING);
    }

    public boolean applyDnsLeakPrevention() {
        return applyLayer(BypassLayer.Type.DNS_LEAK_PREVENTION);
    }

    public boolean applyOsUpdateDomainBlocking() {
        return applyLayer(BypassLayer.Type.OS_UPDATE_DOMAIN_BLOCKING);
    }

    public boolean applyMssMtuClamping(int mss, int mtu) {
        MssMtuClampingLayer layer = (MssMtuClampingLayer) layers.get(BypassLayer.Type.MSS_MTU_CLAMPING);
        if (layer != null) {
            layer.setMssValue(mss);
            layer.setMtuValue(mtu);
        }
        return applyLayer(BypassLayer.Type.MSS_MTU_CLAMPING);
    }

    public boolean applyTrafficShaping() {
        return applyLayer(BypassLayer.Type.TRAFFIC_SHAPING);
    }

    public boolean applyAll() {
        boolean anyOk = false;
        for (BypassLayer.Type type : layerOrder) {
            if (applyLayer(type)) {
                anyOk = true;
            }
        }
        return anyOk;
    }

    private boolean applyLayer(BypassLayer.Type type) {
        BypassLayer layer = layers.get(type);
        if (layer == null) {
            return false;
        }
        BypassLayer.LayerState newState = layer.apply();
        notifyLayer(type, newState);
        return newState == BypassLayer.LayerState.ACTIVE || newState == BypassLayer.LayerState.DEGRADED;
    }

    public boolean restoreIpv6() {
        BypassLayer layer = layers.get(BypassLayer.Type.IPV6_BLOCKING);
        if (layer != null) {
            layer.revert();
            notifyLayer(BypassLayer.Type.IPV6_BLOCKING, BypassLayer.LayerState.DISABLED);
        }
        return true;
    }

    public boolean disableAll() {
        for (BypassLayer.Type type : layerOrder) {
            BypassLayer layer = layers.get(type);
            if (layer != null) {
                layer.revert();
                notifyLayer(type, BypassLayer.LayerState.DISABLED);
            }
        }
        return true;
    }

    /* ================================================================
     * Query helpers (legacy-compatible)
     * ================================================================ */

    public int getTtlValue() {
        BypassLayer layer = layers.get(BypassLayer.Type.TTL_NORMALIZATION);
        if (layer instanceof TtlNormalizationLayer) {
            return ((TtlNormalizationLayer) layer).getTtlValue();
        }
        return 65;
    }

    public int getMssValue() {
        BypassLayer layer = layers.get(BypassLayer.Type.MSS_MTU_CLAMPING);
        if (layer instanceof MssMtuClampingLayer) {
            return ((MssMtuClampingLayer) layer).getMssValue();
        }
        return 1360;
    }

    public int getMtuValue() {
        BypassLayer layer = layers.get(BypassLayer.Type.MSS_MTU_CLAMPING);
        if (layer instanceof MssMtuClampingLayer) {
            return ((MssMtuClampingLayer) layer).getMtuValue();
        }
        return 1400;
    }

    public boolean isRoot() {
        return isRoot;
    }

    public BypassLayer.LayerState getTtlState() {
        return getState(BypassLayer.Type.TTL_NORMALIZATION);
    }

    public BypassLayer.LayerState getIpv6State() {
        return getState(BypassLayer.Type.IPV6_BLOCKING);
    }

    public BypassLayer.LayerState getDnsState() {
        return getState(BypassLayer.Type.DNS_LEAK_PREVENTION);
    }

    public BypassLayer.LayerState getOsUpdateState() {
        return getState(BypassLayer.Type.OS_UPDATE_DOMAIN_BLOCKING);
    }

    public BypassLayer.LayerState getMssState() {
        return getState(BypassLayer.Type.MSS_MTU_CLAMPING);
    }

    public BypassLayer.LayerState getShapingState() {
        return getState(BypassLayer.Type.TRAFFIC_SHAPING);
    }

    private BypassLayer.LayerState getState(BypassLayer.Type type) {
        BypassLayer layer = layers.get(type);
        return layer != null ? layer.getState() : BypassLayer.LayerState.DISABLED;
    }

    public boolean isLayerActive(BypassLayer.Type type) {
        BypassLayer.LayerState state = getState(type);
        return state == BypassLayer.LayerState.ACTIVE;
    }

    public boolean isAnyLayerActive() {
        for (BypassLayer.Type type : layerOrder) {
            if (isLayerActive(type)) {
                return true;
            }
        }
        return false;
    }

    public int activeLayerCount() {
        int count = 0;
        for (BypassLayer.Type type : layerOrder) {
            if (isLayerActive(type)) {
                count++;
            }
        }
        return count;
    }

    public void addListener(BypassListener listener) {
        listeners.add(listener);
    }

    public void removeListener(BypassListener listener) {
        listeners.remove(listener);
    }

    private void notifyLayer(BypassLayer.Type type, BypassLayer.LayerState state) {
        for (BypassListener l : listeners) {
            l.onLayerStateChanged(toLegacyLayer(type), state);
        }
        for (BypassListener l : listeners) {
            l.onBypassStatusChanged();
        }
    }

    /* ================================================================
     * Legacy domain list (backward compat)
     * ================================================================ */

    public void addBlockedDomain(String domain) {
        BypassLayer layer = layers.get(BypassLayer.Type.OS_UPDATE_DOMAIN_BLOCKING);
        if (layer instanceof OsUpdateDomainBlockingLayer) {
            ((OsUpdateDomainBlockingLayer) layer).addBlockedDomain(domain);
        }
    }

    public List<String> getBlockedDomains() {
        BypassLayer layer = layers.get(BypassLayer.Type.OS_UPDATE_DOMAIN_BLOCKING);
        if (layer instanceof OsUpdateDomainBlockingLayer) {
            return ((OsUpdateDomainBlockingLayer) layer).getBlockedDomains();
        }
        return new ArrayList<>();
    }

    public void dumpStatus() {
        Log.i(TAG, "=== Carrier Bypass Status ===");
        Log.i(TAG, "Root: " + isRoot);
        for (BypassLayer.Type type : layerOrder) {
            BypassLayer layer = layers.get(type);
            Log.i(TAG, "  " + layer.getName() + ": " + layer.getState());
        }
        Log.i(TAG, "Active layers: " + activeLayerCount() + "/6");
    }

    /* ================================================================
     * Legacy listener bridge
     * ================================================================ */

    public interface BypassListener {
        void onLayerStateChanged(CarrierBypassLayer layer, LayerState state);
        void onBypassStatusChanged();
    }

    public enum CarrierBypassLayer {
        TTL_NORMALIZATION,
        IPV6_BLOCKING,
        DNS_LEAK_PREVENTION,
        OS_UPDATE_DOMAIN_BLOCKING,
        MSS_MTU_CLAMPING,
        TRAFFIC_SHAPING
    }

    public enum LayerState {
        DISABLED,
        ACTIVE,
        DEGRADED,
        FAILED
    }

    private CarrierBypassLayer toLegacyLayer(BypassLayer.Type type) {
        switch (type) {
            case TTL_NORMALIZATION: return CarrierBypassLayer.TTL_NORMALIZATION;
            case IPV6_BLOCKING: return CarrierBypassLayer.IPV6_BLOCKING;
            case DNS_LEAK_PREVENTION: return CarrierBypassLayer.DNS_LEAK_PREVENTION;
            case OS_UPDATE_DOMAIN_BLOCKING: return CarrierBypassLayer.OS_UPDATE_DOMAIN_BLOCKING;
            case MSS_MTU_CLAMPING: return CarrierBypassLayer.MSS_MTU_CLAMPING;
            case TRAFFIC_SHAPING: return CarrierBypassLayer.TRAFFIC_SHAPING;
            default: return CarrierBypassLayer.TTL_NORMALIZATION;
        }
    }
}