package com.tetherapp.net;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for NetworkInterfaceManager InterfaceInfo parsing.
 */
@RunWith(JUnit4.class)
public class NetworkInterfaceManagerTest {

    @Test
    public void testInterfaceInfoToString() {
        NetworkInterfaceManager.InterfaceInfo info = new NetworkInterfaceManager.InterfaceInfo();
        info.name = "wlan0";
        info.ipv4Address = "192.168.1.50";
        info.ipv6Address = "fe80::1";
        info.mtu = 1500;
        info.isLoopback = false;

        String s = info.toString();
        assertTrue(s.contains("wlan0"));
        assertTrue(s.contains("192.168.1.50"));
        assertTrue(s.contains("1500"));
    }

    @Test
    public void testInterfaceInfoDefaults() {
        NetworkInterfaceManager.InterfaceInfo info = new NetworkInterfaceManager.InterfaceInfo();
        assertEquals(0, info.mtu);
        assertFalse(info.isLoopback);
        assertFalse(info.isPointToPoint);
        assertFalse(info.isVirtual);
        assertNull(info.macAddress);
    }
}
