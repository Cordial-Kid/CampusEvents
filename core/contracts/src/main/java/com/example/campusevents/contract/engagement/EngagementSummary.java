package com.example.campusevents.contract.engagement;

import java.util.Objects;

public final class EngagementSummary {
    public final boolean favorite;
    public final ReminderView reminder;

    public EngagementSummary(boolean favorite, ReminderView reminder) {
        this.favorite = favorite;
        this.reminder = Objects.requireNonNull(reminder, "reminder");
    }
}
