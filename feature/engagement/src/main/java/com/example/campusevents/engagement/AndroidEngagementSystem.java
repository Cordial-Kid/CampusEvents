package com.example.campusevents.engagement;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.example.campusevents.platform.ui.NotificationRouteFactory;
import java.util.Objects;

/** Production alarm and notification adapter. All calls are short and stay off the UI thread. */
public final class AndroidEngagementSystem implements EngagementSystem {
    static final String ALARM_ACTION = "com.example.campusevents.engagement.REMINDER_ALARM";
    private static final String CHANNEL_ID = "campus_event_reminders";

    private final Context context;
    private final AlarmManager alarmManager;
    private final NotificationManager notificationManager;
    private final NotificationRouteFactory routes;

    public AndroidEngagementSystem(Context context, NotificationRouteFactory routes) {
        this.context = Objects.requireNonNull(context, "context").getApplicationContext();
        this.routes = Objects.requireNonNull(routes, "routes");
        alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
        notificationManager =
                (NotificationManager) this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
    }

    @Override public boolean canDeliverNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        if (!notificationManager.areNotificationsEnabled()) return false;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = notificationManager.getNotificationChannel(CHANNEL_ID);
            return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        }
        return true;
    }

    @Override public boolean canScheduleExactAlarms() {
        return Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms();
    }

    @Override public void schedule(ReminderSchedule reminder) {
        PendingIntent pendingIntent = alarmPendingIntent(reminder);
        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, reminder.triggerAt, pendingIntent);
    }

    @Override public void cancel(int scheduleId) {
        PendingIntent pendingIntent = cancelPendingIntent(scheduleId);
        alarmManager.cancel(pendingIntent);
        pendingIntent.cancel();
    }

    @Override public void post(ReminderNotification notification) {
        PendingIntent route = routes.eventDetail(notification.eventId, notification.userId);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.eng_notification_icon)
                .setContentTitle(notification.title)
                .setContentText(notification.text)
                .setStyle(new Notification.BigTextStyle().bigText(notification.text))
                .setContentIntent(route)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_EVENT);
        notificationManager.notify(notification.notificationId, builder.build());
    }

    private PendingIntent alarmPendingIntent(ReminderSchedule reminder) {
        return PendingIntent.getBroadcast(context, reminder.scheduleId,
                ReminderAlarmReceiver.intentFor(context, reminder),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private PendingIntent cancelPendingIntent(int scheduleId) {
        Uri data = Uri.parse("campusevents://engagement/reminder/" + scheduleId);
        android.content.Intent intent = new android.content.Intent(context, ReminderAlarmReceiver.class)
                .setAction(ALARM_ACTION)
                .setData(data);
        return PendingIntent.getBroadcast(context, scheduleId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, context.getString(R.string.eng_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(context.getString(R.string.eng_channel_description));
        notificationManager.createNotificationChannel(channel);
    }
}
