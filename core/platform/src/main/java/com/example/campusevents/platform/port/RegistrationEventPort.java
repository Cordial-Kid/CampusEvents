package com.example.campusevents.platform.port;

import com.example.campusevents.platform.data.TxScope;

public interface RegistrationEventPort {
    int countActive(TxScope tx, long eventId);
    void cancelActiveForEvent(TxScope tx, long eventId);
}
