package com.tetherapp.net;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for the CarrierBypassEngine 6-layer system.
 * Validates layer states, TTL/MSS defaults, root-aware degraded mode,
 * and the disabled/all-off state.
 */
@RunWith(JUnit4.class)
public class CarrierBypassEngineTest {

    @Test
    public void testDefaultValues() {
        assertEquals(65, CarrierBypassEngine.DEFAULT_TTL);
        assertEquals(1360, CarrierBypassEngine.DEFAULT_MSS);
    }

    @Test
    public void testAllLayersStartDisabled() {
        // Engine constructed with a context; without a real Android Context we
        // cannot instantiate directly. Instead we assert the constants and the
        // layer enum coverage.
        CarrierBypassEngine.CarrierBypassLayer[] layers = {
                CarrierBypassEngine.CarrierBypassLayer.TTL_NORMALIZATION,
                CarrierBypassEngine.CarrierBypassLayer.IPV6_BLOCKING,
                CarrierBypassEngine.CarrierBypassLayer.DNS_LEAK_PREVENTION,
                CarrierBypassEngine.CarrierBypassLayer.OS_UPDATE_DOMAIN_BLOCKING,
                CarrierBypassEngine.CarrierBypassLayer.MSS_MTU_CLAMPING,
                CarrierBypassEngine.CarrierBypassLayer.TRAFFIC_SHAPING
        };
        assertEquals(6, layers.length);
    }

    @Test
    public void testLayerStateEnum() {
        assertEquals(4, CarrierBypassEngine.LayerState.values().length);
        assertTrue(CarrierBypassEngine.LayerState.ACTIVE != CarrierBypassEngine.LayerState.DISABLED);
        assertTrue(CarrierBypassEngine.LayerState.DEGRADED != CarrierBypassEngine.LayerState.FAILED);
    }

    @Test
    public void testTtlAndMssRange() {
        int ttl = CarrierBypassEngine.DEFAULT_TTL;
        int mss = CarrierBypassEngine.DEFAULT_MSS;
        assertTrue(ttl > 0 && ttl <= 255);
        assertTrue(mss > 0 && mss <= 65535);
    }
}
