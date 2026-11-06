package com.example.campusevents.bootstrap;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.Uri;

import com.example.campusevents.MainActivity;
import com.example.campusevents.platform.Platform;
import com.example.campusevents.platform.ui.*;
import com.example.campusevents.event.*;
import com.example.campusevents.event.ui.EventUiEntry;
import com.example.campusevents.browse.*;
import com.example.campusevents.registration.*;
import com.example.campusevents.engagement.*;
import com.example.campusevents.ui.ShellUiEntry;
import com.example.campusevents.contract.event.EventAdminApi;

import java.util.Arrays;

/** Composition root. No feature receives this object. */
public final class AppGraph {
    public final Platform platform;
    public final UiRegistry ui = new UiRegistry();
    public final ShellNavigator navigator = new ShellNavigator();
    public final EventModule events;
    public final EventAdminApi admin;

    public AppGraph(Context context) {
        platform = new Platform(context, Arrays.asList(
                EventModule.schema(), RegistrationModule.schema(), EngagementModule.schema()));
        events = new EventModule(platform.dataAccess());
        RegistrationModule registrations = new RegistrationModule(platform.dataAccess(), events.readPort());
        EngagementModule engagement = new EngagementModule(platform.dataAccess(), events.readPort(),
                (eventId, userId) -> notification(context, eventId, userId));
        admin = events.createAdmin(registrations.eventPort(), engagement.eventPort());
        BrowseModule browse = new BrowseModule(platform.dataAccess(), events.readPort(),
                registrations.readPort(), engagement.readPort());
        boolean developmentBuild = (context.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        if (!developmentBuild && (!browse.isReady() || !registrations.isReady() || !engagement.isReady())) {
            throw new IllegalStateException("Required feature implementations are still pending");
        }
        BrowseUiEntry browseUi = new BrowseUiEntry(browse.api(), platform.invalidation(), navigator);
        RegistrationUiEntry registrationUi = new RegistrationUiEntry();
        EngagementUiEntry engagementUi = new EngagementUiEntry();
        ui.register((FeatureUiEntry) browseUi);
        ui.register((DetailPanelProvider) browseUi);
        ui.register((FeatureUiEntry) registrationUi);
        ui.register((DetailPanelProvider) registrationUi);
        ui.register((FeatureUiEntry) engagementUi);
        ui.register((DetailPanelProvider) engagementUi);
        ui.register(new EventUiEntry(events.readApi(), admin, navigator));
        ui.register(new ShellUiEntry(ui));
        ui.validateComplete();
        platform.registerEffectHandler(engagement.effectHandler());
        platform.start();
    }

    private PendingIntent notification(Context context, long eventId, long userId) {
        Intent intent = new Intent(context, MainActivity.class)
                .setAction("com.example.campusevents.EVENT_DETAIL")
                .setData(Uri.parse("campusevents://event/" + eventId + "/user/" + userId))
                .putExtra("eventId", eventId)
                .putExtra("recipientUserId", userId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
