package com.example.campusevents.contract.event;

import com.example.campusevents.contract.common.AsyncCall;

public interface EventAdminApi {
    AsyncCall<EventRecord> create(EventInput input);
    AsyncCall<EventRecord> update(long eventId, long expectedVersion, EventInput input);
    AsyncCall<EventRecord> cancel(long eventId, long expectedVersion);
}
