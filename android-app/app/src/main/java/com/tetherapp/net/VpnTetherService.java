package com.tetherapp.net;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.net.VpnService;
import androidx.annotation.Nullable;

public class VpnTetherService extends VpnService {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Nullable
    protected Builder prepareVpnBuilder() {
        return super.prepareBuilder(this);
    }
}
