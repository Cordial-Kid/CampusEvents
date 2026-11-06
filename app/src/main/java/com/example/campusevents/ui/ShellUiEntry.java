package com.example.campusevents.ui;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.campusevents.platform.ui.*;
import java.util.*;
public final class ShellUiEntry implements FeatureUiEntry {
    private final UiRegistry registry;
    public ShellUiEntry(UiRegistry registry) { this.registry=registry; }
    public Set<ScreenKey> screens() { return Collections.singleton(ScreenKey.EVENT_DETAIL); }
    public Fragment createScreen(ScreenKey key, RouteArgs args) {
        Fragment fragment = new DetailFragment(registry);
        Bundle bundle = new Bundle(); bundle.putLong(RouteArgs.EVENT_ID,args.eventId); fragment.setArguments(bundle); return fragment;
    }
    public Set<String> fragmentClassNames() { return Collections.singleton(DetailFragment.class.getName()); }
    public Fragment instantiate(String className) { return new DetailFragment(registry); }
}
