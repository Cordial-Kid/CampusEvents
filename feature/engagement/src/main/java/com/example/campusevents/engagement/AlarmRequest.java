package com.example.campusevents.engagement;

public final class AlarmRequest {
    public final long reminderId;
    public final long eventId;
    public final long userId;
    public final long revision;
    public final long triggerAt;
    public final long epoch;

    public AlarmRequest(long reminderId, long eventId, long userId,
                        long revision, long triggerAt, long epoch) {
        this.reminderId = reminderId;
        this.eventId = eventId;
        this.userId = userId;
        this.revision = revision;
        this.triggerAt = triggerAt;
        this.epoch = epoch;
    }
}
