package com.example.campusevents.engagement;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.engagement.FavoriteApi;
import com.example.campusevents.contract.engagement.FavoriteItem;
import com.example.campusevents.contract.engagement.ReminderApi;
import com.example.campusevents.contract.engagement.ReminderView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EngagementViewModel extends ViewModel {
    final MutableLiveData<Integer> changes = new MutableLiveData<>(0);
    ApiResult<List<FavoriteItem>> mine;
    ApiResult<ReminderView> reminder;
    boolean favorite;
    boolean favoriteKnown;
    boolean busy;
    String message = "";

    private final FavoriteApi favorites;
    private final ReminderApi reminders;
    private final Supplier<AsyncCall<Void>> retryReconcile;
    private final List<CallHandle> reads = new ArrayList<>();
    private CallHandle action;
    private CallHandle reconcile;
    private long requestSequence;

    EngagementViewModel(FavoriteApi favorites, ReminderApi reminders,
                        Supplier<AsyncCall<Void>> retryReconcile) {
        this.favorites = favorites;
        this.reminders = reminders;
        this.retryReconcile = retryReconcile;
    }

    private void signal() {
        Integer value = changes.getValue();
        changes.setValue(value == null ? 1 : value + 1);
    }

    void load(long eventId) {
        cancelReads();
        long sequence = ++requestSequence;
        mine = null;
        reminder = null;
        favorite = false;
        favoriteKnown = false;
        signal();

        reads.add(favorites.listMine().enqueue(result -> {
            if (sequence != requestSequence) return;
            mine = result;
            favoriteKnown = result.success;
            favorite = result.success && containsEvent(result.data, eventId);
            signal();
        }));
        if (eventId > 0) {
            reads.add(reminders.get(eventId).enqueue(result -> {
                if (sequence != requestSequence) return;
                reminder = result;
                signal();
            }));
        }
    }

    void clearIdentity() {
        ++requestSequence;
        cancelReads();
        cancelAction();
        cancelReconcile();
        mine = null;
        reminder = null;
        favorite = false;
        favoriteKnown = false;
        busy = false;
        message = "";
        signal();
    }

    void setFavorite(long eventId) {
        if (busy || !favoriteKnown || eventId <= 0) return;
        beginAction();
        boolean desiredState = !favorite;
        action = favorites.setFavorite(eventId, desiredState).enqueue(result -> {
            finishAction(eventId, result.success ? "收藏操作已完成，正在读取最新状态" : result.message);
        });
    }

    void setReminder(long eventId, int leadMinutes) {
        if (busy || eventId <= 0) return;
        beginAction();
        action = reminders.set(eventId, leadMinutes).enqueue(result -> {
            finishAction(eventId, result.success ? "提醒操作已完成，正在读取最新状态" : result.message);
        });
    }

    void disableReminder(long eventId) {
        if (busy || eventId <= 0) return;
        beginAction();
        action = reminders.disable(eventId).enqueue(result -> {
            finishAction(eventId, result.success ? "提醒已关闭，正在读取最新状态" : result.message);
        });
    }

    void retryReconcile(long eventId) {
        if (busy || eventId <= 0) return;
        beginAction();
        try {
            AsyncCall<Void> call = retryReconcile.get();
            if (call == null) {
                finishAction(eventId, "提醒同步入口不可用");
                return;
            }
            reconcile = call.enqueue(result -> {
                reconcile = null;
                busy = false;
                message = result.success ? "提醒同步已完成，正在读取最新状态" : result.message;
                signal();
                load(eventId);
            });
        } catch (RuntimeException failure) {
            finishAction(eventId, failure.getMessage() == null ? "提醒同步失败" : failure.getMessage());
        }
    }

    void cancelRequests() {
        ++requestSequence;
        cancelReads();
        cancelAction();
        cancelReconcile();
        busy = false;
    }

    private void beginAction() {
        busy = true;
        message = "正在提交操作…";
        signal();
    }

    private void finishAction(long eventId, String resultMessage) {
        action = null;
        busy = false;
        message = resultMessage == null || resultMessage.isEmpty() ? "操作完成" : resultMessage;
        signal();
        load(eventId);
    }

    private boolean containsEvent(List<FavoriteItem> items, long eventId) {
        if (items == null) return false;
        for (FavoriteItem item : items) {
            if (item.event.id == eventId) return true;
        }
        return false;
    }

    private void cancelReads() {
        for (CallHandle handle : reads) handle.cancelDelivery();
        reads.clear();
    }

    private void cancelAction() {
        if (action != null) action.cancelDelivery();
        action = null;
    }

    private void cancelReconcile() {
        if (reconcile != null) reconcile.cancelDelivery();
        reconcile = null;
    }

    @Override protected void onCleared() {
        cancelRequests();
    }
}
