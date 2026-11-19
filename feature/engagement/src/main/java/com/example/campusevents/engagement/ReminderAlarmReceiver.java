package com.example.campusevents.engagement;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public final class ReminderAlarmReceiver extends BroadcastReceiver {
    static final String EXTRA_REMINDER_ID = "reminderId";
    static final String EXTRA_EVENT_ID = "eventId";
    static final String EXTRA_USER_ID = "userId";
    static final String EXTRA_REVISION = "revision";
    static final String EXTRA_TRIGGER_AT = "triggerAt";
    static final String EXTRA_EPOCH = "epoch";

    static Intent intentFor(Context context, ReminderSchedule reminder) {
        Uri data = Uri.parse("campusevents://engagement/reminder/" + reminder.scheduleId);
        return new Intent(context, ReminderAlarmReceiver.class)
                .setAction(AndroidEngagementSystem.ALARM_ACTION)
                .setData(data)
                .putExtra(EXTRA_REMINDER_ID, reminder.reminderId)
                .putExtra(EXTRA_EVENT_ID, reminder.eventId)
                .putExtra(EXTRA_USER_ID, reminder.userId)
                .putExtra(EXTRA_REVISION, reminder.revision)
                .putExtra(EXTRA_TRIGGER_AT, reminder.triggerAt)
                .putExtra(EXTRA_EPOCH, reminder.epoch);
    }

    @Override public void onReceive(Context context, Intent intent) {
        PendingResult pending = goAsync();
        Runnable finish = pending::finish;
        if (!EngagementRuntime.deliverAlarm(intent, finish)) finish.run();
    }
}
