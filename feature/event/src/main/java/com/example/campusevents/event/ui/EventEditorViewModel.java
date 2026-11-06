package com.example.campusevents.event.ui;

import android.os.Bundle;

import androidx.lifecycle.ViewModel;

import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.event.EventRecord;

public final class EventEditorViewModel extends ViewModel {
    Bundle draft;
    long expectedVersion;
    long originalStart;
    long originalEnd;
    long originalDeadline;
    boolean saving;
    ApiResult<EventRecord> saveResult;
    Runnable onChanged;
    private CallHandle saveCall;

    void save(AsyncCall<EventRecord> operation) {
        if (saving) return;
        saving = true;
        saveResult = null;
        notifyChanged();
        saveCall = operation.enqueue(result -> {
            saving = false;
            saveResult = result;
            notifyChanged();
        });
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }

    @Override protected void onCleared() {
        if (saveCall != null) saveCall.cancelDelivery();
        onChanged = null;
    }
}
