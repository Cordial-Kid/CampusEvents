package com.example.campusevents.contract.common;

/** Stable machine-readable outcomes. User interfaces must not compare message text. */
public enum ResultCode {
    OK,
    NOT_LOGGED_IN,
    FORBIDDEN,
    EVENT_NOT_FOUND,
    INVALID_INPUT,
    EVENT_CANCELLED,
    EVENT_STARTED,
    REGISTRATION_CLOSED,
    EVENT_FULL,
    ALREADY_REGISTERED,
    CAPACITY_TOO_SMALL,
    REMINDER_TIME_PASSED,
    REMINDER_UNAVAILABLE,
    REMINDER_SCHEDULE_FAILED,
    DATABASE_ERROR,
    MODULE_NOT_READY,
    SESSION_CHANGED,
    STALE_DATA,
    INTERNAL_ERROR
}
