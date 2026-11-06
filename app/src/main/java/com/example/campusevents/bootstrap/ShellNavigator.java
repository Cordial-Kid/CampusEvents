package com.example.campusevents.bootstrap;
import com.example.campusevents.MainActivity;
import com.example.campusevents.platform.ui.*;
/** Only the shell knows the Activity. Features receive Navigator. */
public final class ShellNavigator implements Navigator {
    private MainActivity host;
    public void bind(MainActivity activity) { host = activity; }
    public void unbind(MainActivity activity) { if (host == activity) host = null; }
    public void open(Route route) { if (host != null) host.open(route); }
    public void back() { if (host != null) host.getOnBackPressedDispatcher().onBackPressed(); }
}
