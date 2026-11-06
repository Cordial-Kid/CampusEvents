package com.example.campusevents.platform.invalidation;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

public final class DataInvalidation {
    public interface Listener { void onChanged(ChangeSet change); }
    public interface Subscription { void unsubscribe(); }
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    public Subscription subscribe(Listener listener) {
        listeners.addIfAbsent(listener);
        return () -> listeners.remove(listener);
    }

    public void publishCommitted(Collection<ChangeSet> changes) {
        for (ChangeSet change : changes) {
            main.post(() -> {
                for (Listener listener : listeners) {
                    try { listener.onChanged(change); }
                    catch (RuntimeException failure) {
                        Log.e("DataInvalidation", "Listener failed", failure);
                    }
                }
            });
        }
    }
}
