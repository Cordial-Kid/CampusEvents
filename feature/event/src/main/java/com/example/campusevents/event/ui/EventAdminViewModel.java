package com.example.campusevents.event.ui;

import androidx.lifecycle.ViewModel;

import com.example.campusevents.contract.event.EventRecord;

import java.util.Collections;
import java.util.List;

public final class EventAdminViewModel extends ViewModel {
    List<EventRecord> events = Collections.emptyList();
}
