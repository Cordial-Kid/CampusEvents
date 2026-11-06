package com.example.campusevents.platform.ui;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Explicit ownership maps, frozen before activity state restoration starts. */
public final class UiRegistry {
    private final Map<ScreenKey, FeatureUiEntry> screens = new EnumMap<>(ScreenKey.class);
    private final Map<DetailSlot, DetailPanelProvider> panels = new EnumMap<>(DetailSlot.class);
    private final Map<String, FeatureUiEntry> fragments = new HashMap<>();
    private boolean frozen;

    public void register(FeatureUiEntry entry) {
        requireMutable();
        for (ScreenKey screen : entry.screens()) {
            if (screens.containsKey(screen)) throw new IllegalStateException("Duplicate screen " + screen);
        }
        for (String name : entry.fragmentClassNames()) {
            if (fragments.containsKey(name)) throw new IllegalStateException("Duplicate fragment " + name);
        }
        for (ScreenKey screen : entry.screens()) screens.put(screen, entry);
        for (String name : entry.fragmentClassNames()) fragments.put(name, entry);
    }

    public void register(DetailPanelProvider provider) {
        requireMutable();
        if (panels.containsKey(provider.slot())) {
            throw new IllegalStateException("Duplicate detail panel " + provider.slot());
        }
        panels.put(provider.slot(), provider);
    }

    public void validateComplete() {
        for (ScreenKey screen : ScreenKey.values()) {
            if (!screens.containsKey(screen)) throw new IllegalStateException("Missing screen " + screen);
        }
        for (DetailSlot slot : DetailSlot.values()) {
            if (!panels.containsKey(slot)) throw new IllegalStateException("Missing panel " + slot);
        }
        frozen = true;
    }

    public Fragment createScreen(Route route) {
        FeatureUiEntry entry = screens.get(route.screen);
        if (entry == null) throw new IllegalStateException("Unregistered screen " + route.screen);
        Fragment fragment = entry.createScreen(route.screen, route.args);
        requireRestorable(fragment);
        applyArgs(fragment, route.args);
        return fragment;
    }

    public Fragment createPanel(DetailSlot slot, long eventId) {
        DetailPanelProvider provider = panels.get(slot);
        if (provider == null) throw new IllegalStateException("Unregistered panel " + slot);
        Fragment fragment = provider.createPanel(eventId);
        requireRestorable(fragment);
        applyArgs(fragment, RouteArgs.event(eventId));
        return fragment;
    }

    public FragmentFactory fragmentFactory() {
        return new FragmentFactory() {
            @Override public Fragment instantiate(ClassLoader loader, String className) {
                FeatureUiEntry entry = fragments.get(className);
                if (entry == null) return super.instantiate(loader, className);
                return entry.instantiate(className);
            }
        };
    }

    private void requireMutable() {
        if (frozen) throw new IllegalStateException("UI registry already validated");
    }

    private void requireRestorable(Fragment fragment) {
        if (fragment == null || !fragments.containsKey(fragment.getClass().getName())) {
            throw new IllegalStateException("Screen and panel fragments must register a restoration factory");
        }
    }

    private static void applyArgs(Fragment fragment, RouteArgs routeArgs) {
        Bundle args = fragment.getArguments() == null ? new Bundle() : new Bundle(fragment.getArguments());
        args.putAll(routeArgs.toBundle());
        fragment.setArguments(args);
    }
}
