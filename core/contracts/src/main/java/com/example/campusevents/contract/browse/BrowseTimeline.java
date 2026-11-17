package com.example.campusevents.contract.browse;

import com.example.campusevents.contract.session.UserSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Events and the viewer from the same read scope. Viewer is null before login. */
public final class BrowseTimeline {
    public final UserSnapshot viewer;
    public final List<EventView> events;

    public BrowseTimeline(UserSnapshot viewer, List<EventView> events) {
        this.viewer = viewer;
        this.events = Collections.unmodifiableList(new ArrayList<>(events));
    }
}
