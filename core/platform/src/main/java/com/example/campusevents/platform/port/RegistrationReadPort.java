package com.example.campusevents.platform.port;

import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.registration.RegistrationSummary;
import com.example.campusevents.platform.data.ReadScope;
import java.util.List;
import java.util.Map;

public interface RegistrationReadPort {
    ModuleValue<Map<Long, RegistrationSummary>> summaries(ReadScope scope, List<EventRecord> events);
}
