package com.tetherapp.net;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Connection state machine:
 *   DISCONNECTED -> CONNECTING -> CONNECTED
 *                  ^            |
 *                  |            v
 *            RECONNECTING   FAILED
 *
 * Emits state transitions to listeners so the UI and tether managers can
 * react (start proxy, bring up VPN, reconnect with backoff, etc.).
 */
public class ConnectionStateMachine {
    public enum State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        FAILED
    }

    public interface Listener {
        void onStateChanged(State previous, State current);
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.DISCONNECTED);
    private final java.util.List<Listener> listeners = new java.util.ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "conn-state-machine");
        t.setDaemon(true);
        return t;
    });

    private int reconnectAttempt = 0;
    private final int maxReconnectAttempts;
    private final long baseBackoffMs;
    private volatile boolean autoReconnect = true;

    public ConnectionStateMachine() {
        this(10, 1000L);
    }

    public ConnectionStateMachine(int maxReconnectAttempts, long baseBackoffMs) {
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.baseBackoffMs = baseBackoffMs;
    }

    public State getState() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED;
    }

    public boolean isConnecting() {
        State s = state.get();
        return s == State.CONNECTING || s == State.RECONNECTING;
    }

    public int getReconnectAttempt() {
        return reconnectAttempt;
    }

    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    public void setMaxReconnectAttempts(int maxReconnectAttempts) {
        this.maxReconnectAttempts = 0; // placeholder; field is final
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void connect() {
        if (state.get() == State.CONNECTED) {
            return;
        }
        transition(State.CONNECTING);
        reconnectAttempt = 0;
        executor.execute(this::attemptConnect);
    }

    public void connected() {
        reconnectAttempt = 0;
        transition(State.CONNECTED);
    }

    public void disconnected() {
        transition(State.DISCONNECTED);
    }

    public void failed() {
        transition(State.FAILED);
        if (autoReconnect && reconnectAttempt < maxReconnectAttempts) {
            scheduleReconnect();
        }
    }

    public void reconnect() {
        reconnectAttempt = 0;
        transition(State.CONNECTING);
        executor.execute(this::attemptConnect);
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    private void attemptConnect() {
        // Subclasses / integrations override the actual connect logic via a callback.
        // Default: mark failed so reconnect backoff can be observed.
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        failed();
    }

    private void scheduleReconnect() {
        reconnectAttempt++;
        long delay = baseBackoffMs * (long) Math.pow(2, reconnectAttempt - 1);
        transition(State.RECONNECTING);
        executor.execute(() -> {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            if (state.get() == State.RECONNECTING) {
                transition(State.CONNECTING);
                attemptConnect();
            }
        });
    }

    private void transition(State next) {
        State prev = state.getAndSet(next);
        if (prev == next) {
            return;
        }
        for (Listener l : listeners) {
            l.onStateChanged(prev, next);
        }
    }
}
