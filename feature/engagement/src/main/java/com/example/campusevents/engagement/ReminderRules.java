package com.example.campusevents.engagement;

import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.UserSnapshot;

/** Pure reminder rules shared by API validation and the reconciliation pass. */
final class ReminderRules {
    private static final long MILLIS_PER_MINUTE = 60_000L;

    private ReminderRules() {}

    static boolean hasValidLead(int leadMinutes) {
        return leadMinutes > 0;
    }

    static long triggerAt(long startAt, int leadMinutes) {
        if (!hasValidLead(leadMinutes)) {
            throw new IllegalArgumentException("Lead minutes must be positive");
        }
        return startAt - leadMinutes * MILLIS_PER_MINUTE;
    }

    static ResultCode setRejection(UserSnapshot actor, EventRecord event, int leadMinutes,
                                   long triggerAt, long now) {
        if (!hasValidLead(leadMinutes)) return ResultCode.INVALID_INPUT;
        if (actor == null) return ResultCode.NOT_LOGGED_IN;
        if (actor.role != Role.STUDENT) return ResultCode.FORBIDDEN;
        if (event == null) return ResultCode.EVENT_NOT_FOUND;
        if (event.status == EventStatus.CANCELLED) return ResultCode.EVENT_CANCELLED;
        if (now >= event.startAt) return ResultCode.EVENT_STARTED;
        if (triggerAt <= now) return ResultCode.REMINDER_TIME_PASSED;
        return ResultCode.OK;
    }

    static boolean isSchedulable(EventRecord event, long triggerAt, long now) {
        return event != null
                && event.status == EventStatus.ACTIVE
                && now < event.startAt
                && triggerAt < event.startAt
                && triggerAt > now;
    }
}
