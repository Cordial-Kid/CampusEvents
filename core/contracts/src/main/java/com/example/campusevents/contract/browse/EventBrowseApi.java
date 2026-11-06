package com.example.campusevents.contract.browse;

import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.event.EventFilter;
import java.util.List;

public interface EventBrowseApi {
    AsyncCall<List<EventView>> list(EventFilter filter);
    AsyncCall<EventView> detail(long eventId);
}
