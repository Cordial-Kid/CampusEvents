package com.example.campusevents.engagement;

public final class ReminderSchedule {
    public final int scheduleId;
    public final long reminderId;
    public final long eventId;
    public final long userId;
    public final long revision;
    public final long triggerAt;
    public final long epoch;

    public ReminderSchedule(int scheduleId, long reminderId, long eventId, long userId,
                            long revision, long triggerAt, long epoch) {
        this.scheduleId = scheduleId;
        this.reminderId = reminderId;
        this.eventId = eventId;
        this.userId = userId;
        this.revision = revision;
        this.triggerAt = triggerAt;
        this.epoch = epoch;
    }
}
