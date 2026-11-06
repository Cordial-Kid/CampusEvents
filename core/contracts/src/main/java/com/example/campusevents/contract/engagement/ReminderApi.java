package com.example.campusevents.contract.engagement;

import com.example.campusevents.contract.common.AsyncCall;

public interface ReminderApi {
    AsyncCall<ReminderView> get(long eventId);
    /** Completes after this scheduling attempt, including any permission or system error. */
    AsyncCall<ReminderView> set(long eventId, int leadMinutes);
    AsyncCall<ReminderView> disable(long eventId);
}
