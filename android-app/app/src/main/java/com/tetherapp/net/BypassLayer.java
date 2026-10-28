package com.tetherapp.net;

/**
 * Single layer of the carrier bypass engine. Each layer is independently
 * applyable, revertable, and queryable. The engine composes layers and
 * reports per-layer status without knowing implementation details.
 */
public interface BypassLayer {

    /** Unique layer identifier (matches BypassLayer.Type enum values). */
    Type getType();

    /** Human-readable layer name for UI display. */
    String getName();

    /** Whether this layer requires root privileges to enforce. */
    boolean requiresRoot();

    /** Current enforcement state. */
    LayerState getState();

    /**
     * Apply the layer. Returns ACTIVE on success, DEGRADED if best-effort
     * without root, FAILED if the operation could not be performed.
     */
    LayerState apply();

    /** Revert the layer, restoring previous system state. */
    void revert();

    /** All layer types supported by the engine. */
    enum Type {
        TTL_NORMALIZATION,
        IPV6_BLOCKING,
        DNS_LEAK_PREVENTION,
        OS_UPDATE_DOMAIN_BLOCKING,
        MSS_MTU_CLAMPING,
        TRAFFIC_SHAPING
    }

    /** Enforcement state for a single bypass layer. */
    enum LayerState {
        DISABLED,
        ACTIVE,
        DEGRADED,
        FAILED
    }
}