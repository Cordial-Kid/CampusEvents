package com.example.campusevents.platform.ui;

import androidx.fragment.app.Fragment;
import java.util.Set;

public interface FeatureUiEntry {
    Set<ScreenKey> screens();
    Fragment createScreen(ScreenKey key, RouteArgs args);
    Set<String> fragmentClassNames();
    Fragment instantiate(String fragmentClassName);
}
