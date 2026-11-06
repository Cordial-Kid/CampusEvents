package com.example.campusevents.browse;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.campusevents.contract.browse.EventBrowseApi;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.ui.*;
import java.util.*;
public final class BrowseUiEntry implements FeatureUiEntry, DetailPanelProvider {
    private final EventBrowseApi api;
    private final DataInvalidation invalidation;
    private final Navigator navigator;
    public BrowseUiEntry(EventBrowseApi api, DataInvalidation invalidation, Navigator navigator) {
        this.api=api; this.invalidation=invalidation; this.navigator=navigator;
    }
    public Set<ScreenKey> screens() { return Collections.singleton(ScreenKey.EVENT_LIST); }
    public Fragment createScreen(ScreenKey key, RouteArgs args) { return fragment(0); }
    private Fragment fragment(long eventId) {
        Fragment fragment = new BrowseFragment(api,invalidation,navigator);
        Bundle args = new Bundle(); args.putLong(RouteArgs.EVENT_ID,eventId); fragment.setArguments(args); return fragment;
    }
    public DetailSlot slot() { return DetailSlot.CONTENT; }
    public Fragment createPanel(long eventId) { return fragment(eventId); }
    public Set<String> fragmentClassNames() { return Collections.singleton(BrowseFragment.class.getName()); }
    public Fragment instantiate(String className) { return new BrowseFragment(api,invalidation,navigator); }
}
