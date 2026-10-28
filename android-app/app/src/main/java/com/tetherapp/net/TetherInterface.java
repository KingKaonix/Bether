package com.tetherapp.net;

import android.content.Context;

/**
 * Common interface for all tethering transports (USB, Wi-Fi, Bluetooth, etc.).
 * Implementations expose start/stop/isActive plus transport-specific metadata.
 * HostActivity and any future UI layer depend only on this interface, not
 * on concrete transport classes.
 */
public interface TetherInterface {

    /** Human-readable transport name (e.g. "USB", "Wi-Fi Hotspot", "Bluetooth DUN"). */
    String getName();

    /** Transport mode (used by TetherMode enum in shared models). */
    TetherMode getMode();

    /** Whether this transport is currently tethering an active connection. */
    boolean isTetheringActive();

    /** Start tethering. Returns true if started (or already active). */
    boolean startTethering();

    /** Stop tethering. Returns true if stopped (or already inactive). */
    boolean stopTethering();

    /** Attach a listener for state changes. */
    void addListener(TetherListener listener);

    /** Remove a previously attached listener. */
    void removeListener(TetherListener listener);

    /** Shut down background threads / release resources. Call from onDestroy. */
    void shutdown();

    /** Listener for tethering state changes. */
    interface TetherListener {
        void onTetheringStarted(String transportName);
        void onTetheringStopped(String transportName);
        void onError(String transportName, String message);
    }
}