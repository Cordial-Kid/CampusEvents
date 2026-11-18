package com.example.campusevents.registration;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.ui.*;
import java.util.*;
public final class RegistrationUiEntry implements FeatureUiEntry, DetailPanelProvider {
    private final RegistrationApi api;
    private final AttendanceApi attendance;
    private final DataInvalidation invalidation;
    private final Navigator navigator;
    public RegistrationUiEntry(RegistrationApi api, AttendanceApi attendance, DataInvalidation invalidation, Navigator navigator) {
        this.api=api;this.attendance=attendance;this.invalidation=invalidation;this.navigator=navigator;
    }
    public Set<ScreenKey> screens() { return Collections.singleton(ScreenKey.MY_REGISTRATIONS); }
    public Fragment createScreen(ScreenKey key, RouteArgs args) {
        if(key!=ScreenKey.MY_REGISTRATIONS) throw new IllegalArgumentException("Unsupported screen");
        return createPanel(0);
    }
    public Set<String> fragmentClassNames() { return Collections.singleton(RegistrationFragment.class.getName()); }
    public Fragment instantiate(String name) {
        if(!name.equals(RegistrationFragment.class.getName())) throw new IllegalArgumentException(name);
        return new RegistrationFragment(api,attendance,invalidation,navigator);
    }
    public DetailSlot slot() { return DetailSlot.REGISTRATION; }
    public Fragment createPanel(long id) {
        Fragment fragment=instantiate(RegistrationFragment.class.getName());
        Bundle args=new Bundle();args.putLong(RouteArgs.EVENT_ID,id);fragment.setArguments(args);return fragment;
    }
}
