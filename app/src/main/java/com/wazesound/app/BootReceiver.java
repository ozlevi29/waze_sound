package com.wazesound.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (new Prefs(context).enabled()) {
            context.startForegroundService(new Intent(context, SoundService.class));
        }
    }
}
