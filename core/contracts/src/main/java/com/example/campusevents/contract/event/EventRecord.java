package com.example.campusevents.contract.event;

import java.util.Objects;

/** Database-independent activity snapshot. All timestamps are Unix milliseconds. */
public final class EventRecord {
    public final long id;
    public final String title;
    public final String description;
    public final String location;
    public final long startAt;
    public final long endAt;
    public final long registrationDeadline;
    public final int capacity;
    public final String category;
    public final SourceType sourceType;
    public final String sourceName;
    public final String sourceUrl;
    public final EventStatus status;
    public final long createdBy;
    public final long createdAt;
    public final long updatedAt;
    public final long version;

    public EventRecord(long id, String title, String description, String location,
                       long startAt, long endAt, long registrationDeadline, int capacity,
                       String category, SourceType sourceType, String sourceName,
                       String sourceUrl, EventStatus status, long createdBy,
                       long createdAt, long updatedAt, long version) {
        this.id = id;
        this.title = Objects.requireNonNull(title, "title");
        this.description = Objects.requireNonNull(description, "description");
        this.location = Objects.requireNonNull(location, "location");
        this.startAt = startAt;
        this.endAt = endAt;
        this.registrationDeadline = registrationDeadline;
        this.capacity = capacity;
        this.category = Objects.requireNonNull(category, "category");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName");
        this.sourceUrl = Objects.requireNonNull(sourceUrl, "sourceUrl");
        this.status = Objects.requireNonNull(status, "status");
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.version = version;
    }
}
