package com.example.campusevents.engagement;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.engagement.FavoriteApi;
import com.example.campusevents.contract.engagement.ReminderApi;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.ui.DetailPanelProvider;
import com.example.campusevents.platform.ui.DetailSlot;
import com.example.campusevents.platform.ui.FeatureUiEntry;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.RouteArgs;
import com.example.campusevents.platform.ui.ScreenKey;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class EngagementUiEntry implements FeatureUiEntry, DetailPanelProvider {
    private final FavoriteApi favorites;
    private final ReminderApi reminders;
    private final Supplier<AsyncCall<Void>> retryReconcile;
    private final DataInvalidation invalidation;
    private final Navigator navigator;

    public EngagementUiEntry(FavoriteApi favorites, ReminderApi reminders,
                             Supplier<AsyncCall<Void>> retryReconcile,
                             DataInvalidation invalidation, Navigator navigator) {
        this.favorites = Objects.requireNonNull(favorites, "favorites");
        this.reminders = Objects.requireNonNull(reminders, "reminders");
        this.retryReconcile = Objects.requireNonNull(retryReconcile, "retryReconcile");
        this.invalidation = Objects.requireNonNull(invalidation, "invalidation");
        this.navigator = Objects.requireNonNull(navigator, "navigator");
    }

    @Override public Set<ScreenKey> screens() {
        return Collections.singleton(ScreenKey.MY_FAVORITES);
    }

    @Override public Fragment createScreen(ScreenKey key, RouteArgs args) {
        if (key != ScreenKey.MY_FAVORITES) {
            throw new IllegalArgumentException("Unsupported screen " + key);
        }
        return createFragment(0);
    }

    @Override public Set<String> fragmentClassNames() {
        return Collections.singleton(EngagementFragment.class.getName());
    }

    @Override public Fragment instantiate(String fragmentClassName) {
        if (!EngagementFragment.class.getName().equals(fragmentClassName)) {
            throw new IllegalArgumentException(fragmentClassName);
        }
        return new EngagementFragment(favorites, reminders, retryReconcile, invalidation, navigator);
    }

    @Override public DetailSlot slot() {
        return DetailSlot.ENGAGEMENT;
    }

    @Override public Fragment createPanel(long eventId) {
        if (eventId <= 0) {
            throw new IllegalArgumentException("Positive event id required");
        }
        return createFragment(eventId);
    }

    private Fragment createFragment(long eventId) {
        Fragment fragment = instantiate(EngagementFragment.class.getName());
        Bundle args = new Bundle();
        args.putLong(RouteArgs.EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }
}
