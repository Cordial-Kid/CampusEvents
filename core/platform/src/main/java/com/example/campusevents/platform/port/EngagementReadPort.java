package com.example.campusevents.platform.port;

import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.engagement.EngagementSummary;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.platform.data.ReadScope;
import java.util.List;
import java.util.Map;

public interface EngagementReadPort {
    ModuleValue<Map<Long, EngagementSummary>> summaries(ReadScope scope, List<EventRecord> events);
}
