package de.content_space.nightcolors;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NightColorsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            SetScreenColorService.installAlarms(context);
        } else {
            SetScreenColorService.sendWakefulWork(context, action);
        }
    }
}
