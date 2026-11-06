package com.example.campusevents.contract.event;

/** Editable fields. Validation belongs to the event command, so invalid forms yield ApiResult. */
public final class EventInput {
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

    public EventInput(String title, String description, String location, long startAt,
                      long endAt, long registrationDeadline, int capacity, String category,
                      SourceType sourceType, String sourceName, String sourceUrl) {
        this.title = title;
        this.description = description;
        this.location = location;
        this.startAt = startAt;
        this.endAt = endAt;
        this.registrationDeadline = registrationDeadline;
        this.capacity = capacity;
        this.category = category;
        this.sourceType = sourceType;
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
    }
}
