package com.example.campusevents.platform.ui;

import androidx.fragment.app.Fragment;

public interface DetailPanelProvider {
    DetailSlot slot();
    Fragment createPanel(long eventId);
}
