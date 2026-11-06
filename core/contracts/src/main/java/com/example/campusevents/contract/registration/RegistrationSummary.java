package com.example.campusevents.contract.registration;

import com.example.campusevents.contract.common.ResultCode;
import java.util.Objects;

/** C owns all eligibility rules. Other modules display this evaluated snapshot. */
public final class RegistrationSummary {
    public final int activeCount;
    public final int remainingCapacity;
    public final RegistrationStatus myStatus;
    public final CancelReason cancelReason;
    public final boolean canRegister;
    /** OK when registration is permitted, otherwise the stable rejection reason. */
    public final ResultCode rejectionCode;
    public final boolean canCancel;

    public RegistrationSummary(int activeCount, int remainingCapacity,
                               RegistrationStatus myStatus, CancelReason cancelReason,
                               boolean canRegister, ResultCode rejectionCode,
                               boolean canCancel) {
        this.activeCount = activeCount;
        this.remainingCapacity = remainingCapacity;
        this.myStatus = Objects.requireNonNull(myStatus, "myStatus");
        this.cancelReason = Objects.requireNonNull(cancelReason, "cancelReason");
        this.canRegister = canRegister;
        this.rejectionCode = Objects.requireNonNull(rejectionCode, "rejectionCode");
        this.canCancel = canCancel;
    }
}
