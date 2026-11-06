package com.example.campusevents.event.ui;

import androidx.fragment.app.Fragment;

import com.example.campusevents.contract.event.EventAdminApi;
import com.example.campusevents.contract.event.EventReadApi;
import com.example.campusevents.platform.ui.FeatureUiEntry;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.RouteArgs;
import com.example.campusevents.platform.ui.ScreenKey;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** The shell registers this entry once and uses it again during Fragment restoration. */
public final class EventUiEntry implements FeatureUiEntry {
    private final EventReadApi reads;
    private final EventAdminApi admin;
    private final Navigator navigator;

    public EventUiEntry(EventReadApi reads, EventAdminApi admin, Navigator navigator) {
        this.reads = reads;
        this.admin = admin;
        this.navigator = navigator;
    }

    @Override public Set<ScreenKey> screens() {
        return new HashSet<>(Arrays.asList(ScreenKey.ADMIN_LIST, ScreenKey.ADMIN_CREATE, ScreenKey.ADMIN_EDIT));
    }

    @Override public Fragment createScreen(ScreenKey key, RouteArgs args) {
        if (key == ScreenKey.ADMIN_LIST) return new EventAdminListFragment(reads, admin, navigator);
        if (key == ScreenKey.ADMIN_CREATE) return EventEditorFragment.create(reads, admin, navigator, 0);
        if (key == ScreenKey.ADMIN_EDIT) return EventEditorFragment.create(reads, admin, navigator, args.eventId);
        throw new IllegalArgumentException("Unsupported event screen " + key);
    }

    @Override public Set<String> fragmentClassNames() {
        return new HashSet<>(Arrays.asList(EventAdminListFragment.class.getName(), EventEditorFragment.class.getName()));
    }

    @Override public Fragment instantiate(String name) {
        if (name.equals(EventAdminListFragment.class.getName())) return new EventAdminListFragment(reads, admin, navigator);
        if (name.equals(EventEditorFragment.class.getName())) return new EventEditorFragment(reads, admin, navigator);
        throw new IllegalArgumentException("Unknown event Fragment " + name);
    }
}
