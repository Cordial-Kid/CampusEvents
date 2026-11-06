package com.example.campusevents.contract.browse;

import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.engagement.EngagementSummary;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.registration.RegistrationSummary;
import java.util.Objects;

/** A single read-scope projection. Missing modules are explicitly NOT_READY. */
public final class EventView {
    public final EventRecord event;
    public final long evaluatedAt;
    public final ModuleValue<RegistrationSummary> registration;
    public final ModuleValue<EngagementSummary> engagement;

    public EventView(EventRecord event, long evaluatedAt,
                     ModuleValue<RegistrationSummary> registration,
                     ModuleValue<EngagementSummary> engagement) {
        this.event = Objects.requireNonNull(event, "event");
        this.evaluatedAt = evaluatedAt;
        this.registration = Objects.requireNonNull(registration, "registration");
        this.engagement = Objects.requireNonNull(engagement, "engagement");
    }
}
