package com.example.campusevents.engagement;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.campusevents.platform.ui.*;
import java.util.Collections;
import java.util.Set;
public final class EngagementUiEntry implements FeatureUiEntry, DetailPanelProvider {
    public Set<ScreenKey> screens() { return Collections.singleton(ScreenKey.MY_FAVORITES); }
    public Fragment createScreen(ScreenKey key, RouteArgs args) { return new EngagementPlaceholderFragment(); }
    public Set<String> fragmentClassNames() { return Collections.singleton(EngagementPlaceholderFragment.class.getName()); }
    public Fragment instantiate(String className) { return new EngagementPlaceholderFragment(); }
    public DetailSlot slot() { return DetailSlot.ENGAGEMENT; }
    public Fragment createPanel(long eventId) {
        Fragment panel = new EngagementPlaceholderFragment();
        Bundle args = new Bundle(); args.putLong(RouteArgs.EVENT_ID, eventId); panel.setArguments(args);
        return panel;
    }
}
