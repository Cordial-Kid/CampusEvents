package com.example.campusevents.contract.session;

public final class SessionSnapshot {
    /** Null until the first demonstration identity is selected. */
    public final UserSnapshot user;
    public final long epoch;

    public SessionSnapshot(UserSnapshot user, long epoch) {
        this.user = user;
        this.epoch = epoch;
    }
}
