package com.example.campusevents.browse;
import androidx.lifecycle.*;
import com.example.campusevents.contract.browse.*;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.common.*;
import java.util.*;
public final class BrowseViewModel extends ViewModel {
    final MutableLiveData<ApiResult<List<EventView>>> state = new MutableLiveData<>();
    private final EventBrowseApi api;
    private CallHandle pending;
    private long sequence;
    BrowseViewModel(EventBrowseApi api) { this.api = api; }
    void refresh(long eventId) {
        cancel(); long request = sequence;
        if (eventId > 0) pending = api.detail(eventId).enqueue(result -> {
            if (request != sequence) return;
            state.setValue(result.success ? ApiResult.ok(Collections.singletonList(result.data))
                    : ApiResult.failure(result.code, result.message));
        });
        else pending = api.list(new EventFilter()).enqueue(result -> {
            if (request == sequence) state.setValue(result);
        });
    }
    void cancel() { sequence++; if (pending != null) pending.cancelDelivery(); }
    @Override protected void onCleared() { cancel(); }
}
