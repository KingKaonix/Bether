package com.tetherapp.net;

import android.app.Service;
import android.content.Intent;
import android.hardware.usb.UsbManager;
import android.os.IBinder;
import androidx.annotation.Nullable;

public class UsbTetherService extends Service {
    private UsbManager usbManager;

    @Override
    public void onCreate() {
        super.onCreate();
        usbManager = (UsbManager) getSystemService(USB_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
