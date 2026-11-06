package com.example.campusevents.platform.invalidation;

import java.util.Objects;

public final class ChangeSet {
    public final ChangeTopic topic;
    /** Zero means all events. */
    public final long eventId;
    public ChangeSet(ChangeTopic topic, long eventId) {
        if (eventId < 0) throw new IllegalArgumentException("Negative event id");
        this.topic = Objects.requireNonNull(topic);
        this.eventId = eventId;
    }
    @Override public boolean equals(Object other) {
        if (!(other instanceof ChangeSet)) return false;
        ChangeSet change = (ChangeSet) other;
        return topic == change.topic && eventId == change.eventId;
    }
    @Override public int hashCode() { return 31 * topic.hashCode() + Long.hashCode(eventId); }
}
