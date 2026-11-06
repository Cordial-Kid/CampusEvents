package com.example.campusevents.contract.registration;

import com.example.campusevents.contract.event.EventRecord;
import java.util.Objects;

public final class RegistrationItem {
    public final long id;
    public final EventRecord event;
    public final RegistrationStatus status;
    public final CancelReason cancelReason;
    public final long registeredAt;
    public final Long cancelledAt;

    public RegistrationItem(long id, EventRecord event, RegistrationStatus status,
                            CancelReason cancelReason, long registeredAt, Long cancelledAt) {
        this.id = id;
        this.event = Objects.requireNonNull(event, "event");
        this.status = Objects.requireNonNull(status, "status");
        this.cancelReason = Objects.requireNonNull(cancelReason, "cancelReason");
        this.registeredAt = registeredAt;
        this.cancelledAt = cancelledAt;
    }
}
