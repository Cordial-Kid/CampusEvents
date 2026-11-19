package com.example.campusevents.engagement;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.engagement.DeliveryState;
import com.example.campusevents.contract.engagement.FavoriteApi;
import com.example.campusevents.contract.engagement.FavoriteItem;
import com.example.campusevents.contract.engagement.ReminderApi;
import com.example.campusevents.contract.engagement.ReminderStatus;
import com.example.campusevents.contract.engagement.ReminderView;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.Route;
import com.example.campusevents.platform.ui.RouteArgs;
import com.example.campusevents.platform.ui.ScreenKey;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Supplier;

public final class EngagementFragment extends Fragment {
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4101;
    private static final TimeZone SHANGHAI = TimeZone.getTimeZone("Asia/Shanghai");

    private final FavoriteApi favorites;
    private final ReminderApi reminders;
    private final Supplier<AsyncCall<Void>> retryReconcile;
    private final DataInvalidation invalidation;
    private final Navigator navigator;

    private EngagementViewModel model;
    private DataInvalidation.Subscription subscription;
    private long eventId;
    private boolean systemStateInitialized;
    private boolean notificationPermission;
    private boolean exactAlarmPermission;

    public EngagementFragment(FavoriteApi favorites, ReminderApi reminders,
                              Supplier<AsyncCall<Void>> retryReconcile,
                              DataInvalidation invalidation, Navigator navigator) {
        super(R.layout.eng_content);
        this.favorites = favorites;
        this.reminders = reminders;
        this.retryReconcile = retryReconcile;
        this.invalidation = invalidation;
        this.navigator = navigator;
    }

    @Override public void onViewCreated(View view, Bundle state) {
        super.onViewCreated(view, state);
        eventId = RouteArgs.fromBundle(getArguments()).eventId;
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {
                return type.cast(new EngagementViewModel(favorites, reminders, retryReconcile));
            }
        }).get(EngagementViewModel.class);

        ((TextView) view.findViewById(R.id.eng_title)).setText(
                eventId == 0 ? R.string.eng_favorites_title : R.string.eng_detail_title);
        view.findViewById(R.id.eng_mine).setVisibility(eventId == 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.eng_detail).setVisibility(eventId > 0 ? View.VISIBLE : View.GONE);
        view.findViewById(R.id.eng_refresh).setOnClickListener(v -> model.load(eventId));
        view.findViewById(R.id.eng_favorite).setOnClickListener(v -> model.setFavorite(eventId));
        view.findViewById(R.id.eng_reminder_5).setOnClickListener(v -> model.setReminder(eventId, 5));
        view.findViewById(R.id.eng_reminder_15).setOnClickListener(v -> model.setReminder(eventId, 15));
        view.findViewById(R.id.eng_reminder_30).setOnClickListener(v -> model.setReminder(eventId, 30));
        view.findViewById(R.id.eng_reminder_60).setOnClickListener(v -> model.setReminder(eventId, 60));
        view.findViewById(R.id.eng_reminder_disable).setOnClickListener(v -> model.disableReminder(eventId));
        view.findViewById(R.id.eng_reminder_retry).setOnClickListener(v -> model.retryReconcile(eventId));
        view.findViewById(R.id.eng_notification_permission).setOnClickListener(v -> requestNotificationPermission());
        view.findViewById(R.id.eng_exact_alarm_permission).setOnClickListener(v -> openExactAlarmSettings());

        model.changes.observe(getViewLifecycleOwner(), ignored -> render(view));
        subscription = invalidation.subscribe(change -> {
            if (change.topic == ChangeTopic.SESSION) {
                model.clearIdentity();
                model.load(eventId);
            } else if (change.topic == ChangeTopic.ENGAGEMENT
                    || change.topic == ChangeTopic.REMINDER_DELIVERY
                    || change.topic == ChangeTopic.EVENT_CONTENT) {
                model.load(eventId);
            }
        });
        systemStateInitialized = false;
        render(view);
    }

    @Override public void onResume() {
        super.onResume();
        if (model == null) return;
        boolean currentNotificationPermission = hasNotificationPermission();
        boolean currentExactAlarmPermission = hasExactAlarmPermission();
        boolean newlyAvailable = systemStateInitialized
                && ((!notificationPermission && currentNotificationPermission)
                || (!exactAlarmPermission && currentExactAlarmPermission));
        notificationPermission = currentNotificationPermission;
        exactAlarmPermission = currentExactAlarmPermission;
        systemStateInitialized = true;
        if (newlyAvailable) {
            model.retryReconcile(eventId);
        } else {
            model.load(eventId);
        }
    }

    @Override public void onDestroyView() {
        if (subscription != null) subscription.unsubscribe();
        subscription = null;
        if (model != null) model.cancelRequests();
        systemStateInitialized = false;
        super.onDestroyView();
    }

    private void render(View view) {
        ((TextView) view.findViewById(R.id.eng_status)).setText(
                model.message.isEmpty() ? getString(R.string.eng_ready) : model.message);
        view.findViewById(R.id.eng_refresh).setEnabled(!model.busy);
        if (eventId == 0) {
            renderMine(view);
        } else {
            renderDetail(view);
        }
    }

    private void renderMine(View view) {
        TextView status = view.findViewById(R.id.eng_mine_status);
        LinearLayout rows = view.findViewById(R.id.eng_rows);
        rows.removeAllViews();
        if (model.mine == null) {
            status.setText(R.string.eng_loading);
            return;
        }
        if (!model.mine.success) {
            status.setText(failureMessage(model.mine, true));
            return;
        }
        List<FavoriteItem> items = model.mine.data;
        if (items.isEmpty()) {
            status.setText(R.string.eng_empty_favorites);
            return;
        }
        status.setText(getString(R.string.eng_favorites_count, items.size()));
        for (FavoriteItem item : items) addFavoriteRow(rows, item);
    }

    private void addFavoriteRow(LinearLayout rows, FavoriteItem item) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, 16, 0, 16);

        TextView text = new TextView(requireContext());
        text.setTextSize(16);
        String cancelled = item.event.status == EventStatus.CANCELLED
                ? "\n" + getString(R.string.eng_event_cancelled) : "";
        text.setText(getString(R.string.eng_favorite_row, item.event.title,
                formatTime(item.event.startAt), item.event.location, cancelled));
        row.addView(text);

        Button open = new Button(requireContext());
        open.setText(R.string.eng_open_event);
        open.setOnClickListener(v -> navigator.open(Route.event(ScreenKey.EVENT_DETAIL, item.event.id)));
        row.addView(open);
        rows.addView(row);
    }

    private void renderDetail(View view) {
        renderFavorite(view);
        renderReminder(view);
        renderSystemAccess(view);
    }

    private void renderFavorite(View view) {
        TextView status = view.findViewById(R.id.eng_favorite_status);
        Button action = view.findViewById(R.id.eng_favorite);
        if (model.mine == null) {
            status.setText(R.string.eng_loading_favorite);
            action.setEnabled(false);
            return;
        }
        if (!model.mine.success) {
            status.setText(failureMessage(model.mine, false));
            action.setEnabled(false);
            return;
        }
        status.setText(model.favorite
                ? R.string.eng_favorite_status_on : R.string.eng_favorite_status_off);
        action.setText(model.favorite ? R.string.eng_cancel_favorite : R.string.eng_add_favorite);
        action.setEnabled(!model.busy);
    }

    private void renderReminder(View view) {
        TextView status = view.findViewById(R.id.eng_reminder_status);
        boolean enabled = model.reminder != null && model.reminder.success && !model.busy;
        if (model.reminder == null) {
            status.setText(R.string.eng_loading_reminder);
        } else if (!model.reminder.success) {
            status.setText(failureMessage(model.reminder, false));
        } else {
            status.setText(reminderText(model.reminder.data));
        }
        view.findViewById(R.id.eng_reminder_5).setEnabled(enabled);
        view.findViewById(R.id.eng_reminder_15).setEnabled(enabled);
        view.findViewById(R.id.eng_reminder_30).setEnabled(enabled);
        view.findViewById(R.id.eng_reminder_60).setEnabled(enabled);
        view.findViewById(R.id.eng_reminder_disable).setEnabled(enabled);
        view.findViewById(R.id.eng_reminder_retry).setEnabled(!model.busy);
    }

    private void renderSystemAccess(View view) {
        TextView status = view.findViewById(R.id.eng_permission_status);
        Button notification = view.findViewById(R.id.eng_notification_permission);
        Button exactAlarm = view.findViewById(R.id.eng_exact_alarm_permission);

        boolean notificationAllowed = hasNotificationPermission();
        boolean exactAlarmAllowed = hasExactAlarmPermission();
        boolean reminderBlocked = model.reminder != null && model.reminder.success
                && model.reminder.data.configured
                && model.reminder.data.deliveryState == DeliveryState.BLOCKED;
        String text = getString(notificationAllowed
                ? R.string.eng_notification_allowed : R.string.eng_notification_required);
        if (Build.VERSION.SDK_INT >= 31) {
            text += "\n" + getString(exactAlarmAllowed
                    ? R.string.eng_exact_alarm_allowed : R.string.eng_exact_alarm_required);
        }
        status.setText(text);
        notification.setVisibility(!notificationAllowed || reminderBlocked
                ? View.VISIBLE : View.GONE);
        notification.setText(Build.VERSION.SDK_INT >= 33 && !runtimeNotificationPermission()
                ? R.string.eng_request_notification
                : R.string.eng_open_notification_settings);
        exactAlarm.setVisibility(Build.VERSION.SDK_INT >= 31 && !exactAlarmAllowed
                ? View.VISIBLE : View.GONE);
        notification.setEnabled(!model.busy);
        exactAlarm.setEnabled(!model.busy);
    }

    private String reminderText(ReminderView value) {
        if (!value.configured) return getString(R.string.eng_reminder_unconfigured);
        String error = value.deliveryError == ResultCode.OK
                ? getString(R.string.eng_no_delivery_error) : value.deliveryError.name();
        return getString(R.string.eng_reminder_details, value.leadMinutes,
                formatTime(value.triggerAt), reminderStatus(value.status),
                deliveryState(value.deliveryState), error);
    }

    private String reminderStatus(ReminderStatus status) {
        switch (status) {
            case PENDING: return getString(R.string.eng_reminder_status_pending);
            case DISABLED: return getString(R.string.eng_reminder_status_disabled);
            case SENT: return getString(R.string.eng_reminder_status_sent);
            case EXPIRED: return getString(R.string.eng_reminder_status_expired);
            default: return status.name();
        }
    }

    private String deliveryState(DeliveryState state) {
        switch (state) {
            case PENDING: return getString(R.string.eng_delivery_pending);
            case SCHEDULED: return getString(R.string.eng_delivery_scheduled);
            case BLOCKED: return getString(R.string.eng_delivery_blocked);
            case FAILED: return getString(R.string.eng_delivery_failed);
            case NOT_REQUIRED: return getString(R.string.eng_delivery_not_required);
            default: return state.name();
        }
    }

    private String failureMessage(ApiResult<?> result, boolean mine) {
        switch (result.code) {
            case NOT_LOGGED_IN:
                return getString(mine ? R.string.eng_login_for_favorites : R.string.eng_login_for_engagement);
            case FORBIDDEN:
                return getString(R.string.eng_admin_forbidden);
            case MODULE_NOT_READY:
                return getString(R.string.eng_module_not_ready);
            case SESSION_CHANGED:
                return getString(R.string.eng_session_changed);
            case EVENT_CANCELLED:
                return getString(R.string.eng_event_cancelled_message);
            default:
                return result.message == null || result.message.isEmpty()
                        ? result.code.name() : result.message;
        }
    }

    private boolean hasNotificationPermission() {
        return runtimeNotificationPermission() && notificationsEnabled();
    }

    private boolean runtimeNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || requireContext().checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean notificationsEnabled() {
        NotificationManager manager =
                (NotificationManager) requireContext().getSystemService(
                        android.content.Context.NOTIFICATION_SERVICE);
        return manager != null && manager.areNotificationsEnabled();
    }

    private boolean hasExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarmManager = (AlarmManager) requireContext().getSystemService(AlarmManager.class);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !runtimeNotificationPermission()) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION_PERMISSION);
        } else if (!notificationsEnabled()) {
            Intent intent = Build.VERSION.SDK_INT >= 26
                    ? new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE,
                                    requireContext().getPackageName())
                    : new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:" + requireContext().getPackageName()));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException failure) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                                     int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATION_PERMISSION && model != null) {
            if (grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                model.retryReconcile(eventId);
            } else {
                model.load(eventId);
            }
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + requireContext().getPackageName()));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException failure) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        }
    }

    private String formatTime(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(SHANGHAI);
        return format.format(new Date(millis));
    }
}
