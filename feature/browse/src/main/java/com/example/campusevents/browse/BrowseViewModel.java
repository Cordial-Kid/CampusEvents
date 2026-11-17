package com.example.campusevents.browse;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import com.example.campusevents.contract.browse.BrowseTimeline;
import com.example.campusevents.contract.browse.EventBrowseApi;
import com.example.campusevents.contract.browse.EventView;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.event.EventFilter;

public final class BrowseViewModel extends ViewModel {
    final MutableLiveData<ApiResult<BrowseTimeline>> timeline = new MutableLiveData<>();
    final MutableLiveData<ApiResult<EventView>> detail = new MutableLiveData<>();
    private final EventBrowseApi api;
    private CallHandle pending;
    private long sequence;

    BrowseViewModel(EventBrowseApi api) { this.api = api; }

    void loadTimeline(EventFilter filter) {
        cancel();
        long request = sequence;
        pending = api.timeline(filter).enqueue(result -> {
            if (request == sequence) timeline.setValue(result);
        });
    }

    void loadDetail(long eventId) {
        cancel();
        long request = sequence;
        pending = api.detail(eventId).enqueue(result -> {
            if (request == sequence) detail.setValue(result);
        });
    }

    void clear() {
        cancel();
        timeline.setValue(null);
        detail.setValue(null);
    }

    void cancel() {
        sequence++;
        if (pending != null) {
            pending.cancelDelivery();
            pending = null;
        }
    }

    @Override protected void onCleared() { cancel(); }
}
