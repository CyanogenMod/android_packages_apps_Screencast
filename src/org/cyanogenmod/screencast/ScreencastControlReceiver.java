package org.cyanogenmod.screencast;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScreencastControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.startService(intent.setClass(context, ScreencastService.class));
    }
}
