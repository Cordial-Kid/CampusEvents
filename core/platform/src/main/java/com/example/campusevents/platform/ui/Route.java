package com.example.campusevents.platform.ui;

import java.util.Objects;

public final class Route {
    public final ScreenKey screen;
    public final RouteArgs args;
    public Route(ScreenKey screen, RouteArgs args) {
        this.screen = Objects.requireNonNull(screen);
        this.args = Objects.requireNonNull(args);
        if ((screen == ScreenKey.EVENT_DETAIL || screen == ScreenKey.ADMIN_EDIT) && args.eventId <= 0) {
            throw new IllegalArgumentException("Route requires an event id");
        }
    }
    public static Route to(ScreenKey screen) { return new Route(screen, RouteArgs.empty()); }
    public static Route event(ScreenKey screen, long eventId) { return new Route(screen, RouteArgs.event(eventId)); }
}
