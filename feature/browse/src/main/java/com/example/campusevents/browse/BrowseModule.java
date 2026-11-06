package com.example.campusevents.browse;
import com.example.campusevents.contract.browse.*;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.contract.engagement.*;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.platform.data.*;
import com.example.campusevents.platform.port.*;
import java.util.*;

/** A's minimal host data path. B extends presentation and recommendation here. */
public final class BrowseModule implements EventBrowseApi {
    private final DataAccess data;
    private final EventReadPort events;
    private final RegistrationReadPort registrations;
    private final EngagementReadPort engagement;
    public BrowseModule(DataAccess data, EventReadPort events,
                        RegistrationReadPort registrations, EngagementReadPort engagement) {
        this.data = data; this.events = events;
        this.registrations = registrations; this.engagement = engagement;
    }
    public boolean isReady() { return false; }
    public EventBrowseApi api() { return this; }
    public AsyncCall<List<EventView>> list(EventFilter filter) {
        return data.read(scope -> aggregate(scope, events.list(scope, filter)));
    }
    public AsyncCall<EventView> detail(long eventId) {
        return data.read(scope -> aggregate(scope,
            Collections.singletonList(events.require(scope, eventId))).get(0));
    }
    private List<EventView> aggregate(ReadScope scope, List<EventRecord> records) {
        ModuleValue<Map<Long, RegistrationSummary>> reg = registrations.summaries(scope, records);
        ModuleValue<Map<Long, EngagementSummary>> eng = engagement.summaries(scope, records);
        List<EventView> result = new ArrayList<>();
        for (EventRecord event : records) {
            result.add(new EventView(event, scope.now(), value(reg, event.id), value(eng, event.id)));
        }
        return Collections.unmodifiableList(result);
    }
    private static <T> ModuleValue<T> value(ModuleValue<Map<Long,T>> values, long eventId) {
        if (values.state == ModuleValue.State.NOT_READY) return ModuleValue.notReady();
        T item = values.data.get(eventId);
        if (item == null) throw new BusinessException(ResultCode.INTERNAL_ERROR, "模块批量摘要缺少活动 " + eventId);
        return ModuleValue.ready(item);
    }
}
