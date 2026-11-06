package com.example.campusevents.platform.port;

import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.platform.data.TxScope;

public interface ReminderEventPort {
    void onEventTimeChanged(TxScope tx, EventRecord before, EventRecord after);
    void disablePendingForEvent(TxScope tx, long eventId);
}
