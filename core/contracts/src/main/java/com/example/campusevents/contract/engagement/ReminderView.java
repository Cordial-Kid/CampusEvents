package com.example.campusevents.contract.engagement;

import com.example.campusevents.contract.common.ResultCode;
import java.util.Objects;

/** Configured intent and the last system scheduling result are separate states. */
public final class ReminderView {
    public final boolean configured;
    public final Long reminderId;
    public final Integer leadMinutes;
    public final Long triggerAt;
    public final ReminderStatus status;
    public final DeliveryState deliveryState;
    /** Null for unconfigured reminders, OK for a configured reminder without an error. */
    public final ResultCode deliveryError;

    public ReminderView(boolean configured, Long reminderId, Integer leadMinutes,
                        Long triggerAt, ReminderStatus status, DeliveryState deliveryState,
                        ResultCode deliveryError) {
        this.configured = configured;
        if (configured) {
            this.reminderId = Objects.requireNonNull(reminderId, "reminderId");
            this.leadMinutes = Objects.requireNonNull(leadMinutes, "leadMinutes");
            this.triggerAt = Objects.requireNonNull(triggerAt, "triggerAt");
            this.status = Objects.requireNonNull(status, "status");
            this.deliveryState = Objects.requireNonNull(deliveryState, "deliveryState");
            this.deliveryError = Objects.requireNonNull(deliveryError, "deliveryError");
        } else {
            this.reminderId = null;
            this.leadMinutes = null;
            this.triggerAt = null;
            this.status = null;
            this.deliveryState = null;
            this.deliveryError = null;
        }
    }

    public static ReminderView unconfigured() {
        return new ReminderView(false, null, null, null, null, null, null);
    }
}
