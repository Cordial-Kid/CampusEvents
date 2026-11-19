package com.example.campusevents.engagement;

public final class ReminderNotification {
    public final int notificationId;
    public final long reminderId;
    public final long eventId;
    public final long userId;
    public final String title;
    public final String text;

    public ReminderNotification(int notificationId, long reminderId,
                                long eventId, long userId,
                                String title, String text) {
        this.notificationId = notificationId;
        this.reminderId = reminderId;
        this.eventId = eventId;
        this.userId = userId;
        this.title = title;
        this.text = text;
    }
}
