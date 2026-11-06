package com.example.campusevents.registration;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.campusevents.platform.ui.*;
import java.util.Collections;
import java.util.Set;
public final class RegistrationUiEntry implements FeatureUiEntry, DetailPanelProvider {
    public Set<ScreenKey> screens() { return Collections.singleton(ScreenKey.MY_REGISTRATIONS); }
    public Fragment createScreen(ScreenKey key, RouteArgs args) { return new RegistrationPlaceholderFragment(); }
    public Set<String> fragmentClassNames() { return Collections.singleton(RegistrationPlaceholderFragment.class.getName()); }
    public Fragment instantiate(String className) { return new RegistrationPlaceholderFragment(); }
    public DetailSlot slot() { return DetailSlot.REGISTRATION; }
    public Fragment createPanel(long eventId) {
        Fragment panel = new RegistrationPlaceholderFragment();
        Bundle args = new Bundle(); args.putLong(RouteArgs.EVENT_ID, eventId); panel.setArguments(args);
        return panel;
    }
}
