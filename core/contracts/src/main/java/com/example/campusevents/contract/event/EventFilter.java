package com.example.campusevents.contract.event;

/** Optional start-time interval [from, until), exact category and title/location keyword. */
public final class EventFilter {
    public final Long startAtFrom;
    public final Long startAtUntil;
    public final String category;
    public final String keyword;
    public final boolean includeCancelled;

    public EventFilter() {
        this(null, null, null, null, true);
    }

    public EventFilter(Long startAtFrom, Long startAtUntil, String category,
                       String keyword, boolean includeCancelled) {
        this.startAtFrom = startAtFrom;
        this.startAtUntil = startAtUntil;
        this.category = category;
        this.keyword = keyword;
        this.includeCancelled = includeCancelled;
    }
}
