package com.example.campusevents.contract.session;

import java.util.Objects;

public final class UserSnapshot {
    public final long userId;
    public final String displayName;
    public final Role role;

    public UserSnapshot(long userId, String displayName, Role role) {
        this.userId = userId;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.role = Objects.requireNonNull(role, "role");
    }
}
