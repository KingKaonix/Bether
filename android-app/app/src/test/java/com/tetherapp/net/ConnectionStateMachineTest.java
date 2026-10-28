package com.tetherapp.net;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.*;

/**
 * Unit tests for ConnectionStateMachine state transitions.
 */
@RunWith(JUnit4.class)
public class ConnectionStateMachineTest {

    @Test
    public void testInitialStateIsDisconnected() {
        ConnectionStateMachine sm = new ConnectionStateMachine();
        assertEquals(ConnectionStateMachine.State.DISCONNECTED, sm.getState());
        assertFalse(sm.isConnected());
        assertFalse(sm.isConnecting());
    }

    @Test
    public void testConnectTransitionsToConnecting() {
        ConnectionStateMachine sm = new ConnectionStateMachine(3, 100);
        final boolean[] changed = {false};
        sm.addListener((prev, current) -> {
            changed[0] = true;
            assertEquals(ConnectionStateMachine.State.DISCONNECTED, prev);
            assertEquals(ConnectionStateMachine.State.CONNECTING, current);
        });
        sm.connect();
        assertEquals(ConnectionStateMachine.State.CONNECTING, sm.getState());
        assertTrue(changed[0]);
        assertTrue(sm.isConnecting());
        assertFalse(sm.isConnected());
        sm.shutdown();
    }

    @Test
    public void testConnectedThenDisconnected() {
        ConnectionStateMachine sm = new ConnectionStateMachine(3, 100);
        sm.connect();
        sm.connected();
        assertEquals(ConnectionStateMachine.State.CONNECTED, sm.getState());
        assertTrue(sm.isConnected());

        sm.disconnected();
        assertEquals(ConnectionStateMachine.State.DISCONNECTED, sm.getState());
        assertFalse(sm.isConnected());
        sm.shutdown();
    }

    @Test
    public void testFailedTriggersReconnect() {
        ConnectionStateMachine sm = new ConnectionStateMachine(3, 50);
        final int[] reconnectCount = {0};
        sm.addListener((prev, current) -> {
            if (current == ConnectionStateMachine.State.RECONNECTING) {
                reconnectCount[0]++;
            }
        });
        sm.connect();
        sm.failed();
        // Allow the reconnect scheduler to fire
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertTrue(reconnectCount[0] > 0);
        sm.shutdown();
    }

    @Test
    public void testAutoReconnectDisabled() {
        ConnectionStateMachine sm = new ConnectionStateMachine(3, 50);
        sm.setAutoReconnect(false);
        sm.connect();
        sm.failed();
        try {
            Thread.sleep(150);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertEquals(ConnectionStateMachine.State.FAILED, sm.getState());
        sm.shutdown();
    }
}
