package com.example.campusevents.contract.event;

import com.example.campusevents.contract.common.AsyncCall;
import java.util.List;

public interface EventReadApi {
    AsyncCall<List<EventRecord>> list(EventFilter filter);
    AsyncCall<EventRecord> get(long eventId);
}
