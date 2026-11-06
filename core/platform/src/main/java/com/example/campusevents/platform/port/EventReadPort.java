package com.example.campusevents.platform.port;

import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.platform.data.ReadScope;
import java.util.List;

public interface EventReadPort {
    EventRecord require(ReadScope scope, long eventId);
    List<EventRecord> list(ReadScope scope, EventFilter filter);
}
