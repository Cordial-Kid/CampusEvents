package com.example.campusevents.engagement;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Boot, package replacement and exact-alarm permission changes all converge on reconciliation. */
public final class ReminderRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
                        .equals(action)) {
            return;
        }
        PendingResult pending = goAsync();
        Runnable finish = pending::finish;
        if (!EngagementRuntime.requestReconcile(finish)) finish.run();
    }
}
