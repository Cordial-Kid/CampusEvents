package com.example.campusevents.contract.registration;
import com.example.campusevents.contract.common.AsyncCall;
/** Student-only attendance; times are Unix milliseconds. */
public interface AttendanceApi {
    AsyncCall<AttendanceView> get(long eventId);
    AsyncCall<AttendanceView> checkIn(long eventId);
    AsyncCall<AttendanceView> submitFeedback(long eventId, int rating, String feedback);
}
