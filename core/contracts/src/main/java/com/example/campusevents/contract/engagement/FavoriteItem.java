package com.example.campusevents.contract.engagement;

import com.example.campusevents.contract.event.EventRecord;
import java.util.Objects;

public final class FavoriteItem {
    public final EventRecord event;
    public final long favoritedAt;

    public FavoriteItem(EventRecord event, long favoritedAt) {
        this.event = Objects.requireNonNull(event, "event");
        this.favoritedAt = favoritedAt;
    }
}
