package com.tetherapp.net;

/**
 * Represents a bandwidth sampling point with timestamp and transfer statistics.
 */
public class BandwidthSample {
    public final long timestamp;
    public final long bytesSent;
    public final long bytesReceived;
    public final double bitsPerSecondSent;
    public final double bitsPerSecondReceived;

    public BandwidthSample(long timestamp, long bytesSent, long bytesReceived, double bitsPerSecondSent, double bitsPerSecondReceived) {
        this.timestamp = timestamp;
        this.bytesSent = bytesSent;
        this.bytesReceived = bytesReceived;
        this.bitsPerSecondSent = bitsPerSecondSent;
        this.bitsPerSecondReceived = bitsPerSecondReceived;
    }

    public long getTimestamp() { return timestamp; }
    public long getBytesSent() { return bytesSent; }
    public long getBytesReceived() { return bytesReceived; }
    public double getBitsPerSecondSent() { return bitsPerSecondSent; }
    public double getBitsPerSecondReceived() { return bitsPerSecondReceived; }
}
