package com.example.campusevents.platform.ui;

import android.os.Bundle;

public final class RouteArgs {
    public static final String EVENT_ID = "campus.eventId";
    public final long eventId;
    private RouteArgs(long eventId) { this.eventId = eventId; }
    public static RouteArgs empty() { return new RouteArgs(0); }
    public static RouteArgs event(long eventId) {
        if (eventId <= 0) throw new IllegalArgumentException("Positive event id required");
        return new RouteArgs(eventId);
    }
    public Bundle toBundle() {
        Bundle args = new Bundle();
        args.putLong(EVENT_ID, eventId);
        return args;
    }
    public static RouteArgs fromBundle(Bundle args) {
        return new RouteArgs(args == null ? 0 : args.getLong(EVENT_ID, 0));
    }
}
