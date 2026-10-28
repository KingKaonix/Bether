package com.tetherapp.net;

/**
 * Tethering transport mode. Mirrors shared/models/tether.py TetherMode
 * so Android and Linux client stay aligned.
 */
public enum TetherMode {
    USB,
    WIFI,
    BLUETOOTH,
    UNKNOWN
}