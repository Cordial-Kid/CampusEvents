package com.example.campusevents.engagement;

import android.content.Intent;
import android.os.Bundle;

/** Narrow bridge for manifest receivers; it contains no feature or application services. */
final class EngagementRuntime {
    private static volatile EngagementModule module;

    private EngagementRuntime() {}

    static void install(EngagementModule engagementModule) {
        module = engagementModule;
    }

    static boolean deliverAlarm(Intent intent, Runnable finished) {
        EngagementModule current = module;
        if (current == null || intent == null) return false;
        Bundle extras = intent.getExtras();
        if (extras == null) return false;
        AlarmRequest request = new AlarmRequest(
                extras.getLong(ReminderAlarmReceiver.EXTRA_REMINDER_ID),
                extras.getLong(ReminderAlarmReceiver.EXTRA_EVENT_ID),
                extras.getLong(ReminderAlarmReceiver.EXTRA_USER_ID),
                extras.getLong(ReminderAlarmReceiver.EXTRA_REVISION),
                extras.getLong(ReminderAlarmReceiver.EXTRA_TRIGGER_AT),
                extras.getLong(ReminderAlarmReceiver.EXTRA_EPOCH));
        try {
            current.deliverAlarm(request).enqueue(result -> finished.run());
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }

    static boolean requestReconcile(Runnable finished) {
        EngagementModule current = module;
        if (current == null) return false;
        try {
            current.requestReconcile().enqueue(result -> finished.run());
            return true;
        } catch (RuntimeException failure) {
            return false;
        }
    }
}
