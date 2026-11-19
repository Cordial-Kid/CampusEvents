package com.example.campusevents.engagement;

import android.database.Cursor;
import com.example.campusevents.contract.engagement.DeliveryState;
import com.example.campusevents.contract.engagement.ReminderStatus;

final class ReminderRecord {
    final long id;
    final long userId;
    final long eventId;
    final int leadMinutes;
    final long triggerAt;
    final ReminderStatus status;
    final long revision;
    final DeliveryState deliveryState;
    final String deliveryError;
    final int scheduleId;
    final long createdAt;
    final long updatedAt;

    private ReminderRecord(Cursor cursor) {
        id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        userId = cursor.getLong(cursor.getColumnIndexOrThrow("userId"));
        eventId = cursor.getLong(cursor.getColumnIndexOrThrow("eventId"));
        leadMinutes = cursor.getInt(cursor.getColumnIndexOrThrow("leadMinutes"));
        triggerAt = cursor.getLong(cursor.getColumnIndexOrThrow("triggerAt"));
        status = ReminderStatus.valueOf(
                cursor.getString(cursor.getColumnIndexOrThrow("status")));
        revision = cursor.getLong(cursor.getColumnIndexOrThrow("revision"));
        deliveryState = DeliveryState.valueOf(
                cursor.getString(cursor.getColumnIndexOrThrow("deliveryState")));
        deliveryError = cursor.getString(cursor.getColumnIndexOrThrow("deliveryError"));
        scheduleId = cursor.getInt(cursor.getColumnIndexOrThrow("scheduleId"));
        createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("createdAt"));
        updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt"));
    }

    static ReminderRecord read(Cursor cursor) {
        return new ReminderRecord(cursor);
    }
}
