package com.example.campusevents.platform.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.engagement.ReminderStatus;
import com.example.campusevents.contract.engagement.ReminderView;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.engagement.AndroidEngagementSystem;
import com.example.campusevents.engagement.EngagementModule;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.port.EventReadPort;
import com.example.campusevents.platform.ui.NotificationRouteFactory;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Exercises real AlarmManager and NotificationManager delivery on the connected device. */
@RunWith(AndroidJUnit4.class)
public final class AndroidReminderDeliveryTest {
    private static final long EVENT_ID = 1L;

    private Context context;
    private Context isolatedContext;
    private SerialDataAccess data;
    private EngagementModule engagement;
    private EventRecord event;
    private NotificationManager notificationManager;

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantNotificationPermission();
        grantExactAlarmPermission();
        notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancelAll();

        isolatedContext = new IsolatedDatabaseContext(context);
        long startAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(30) + 10_000L;
        event = new EventRecord(EVENT_ID, "真实提醒测试", "系统通知链路", "测试教室",
                startAt, startAt + TimeUnit.HOURS.toMillis(2),
                startAt - TimeUnit.HOURS.toMillis(1), 20, "TECH",
                SourceType.MANUAL, "设备测试", "", EventStatus.ACTIVE, 1, 0, 0, 1);
        SchemaContributor eventFixture = new SchemaContributor() {
            @Override public String id() {
                return "android-delivery-event-fixture";
            }

            @Override public void create(SQLiteDatabase database) {
                database.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY)");
                database.execSQL("INSERT INTO events VALUES(1)");
            }
        };
        data = new SerialDataAccess(new CampusDatabase(isolatedContext,
                Arrays.asList(eventFixture, EngagementModule.schema())),
                new DataInvalidation());
        NotificationRouteFactory routes = (eventId, userId) ->
                PendingIntent.getActivity(context, 0,
                        new Intent(Intent.ACTION_VIEW),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        engagement = new EngagementModule(data, new FixtureEvents(),
                new AndroidEngagementSystem(context, routes));
        data.registerEffectHandler(engagement.effectHandler());
        data.start();
        await(data.switchDemoUser(2));
    }

    @After public void tearDown() throws Exception {
        if (notificationManager != null) notificationManager.cancelAll();
        if (data != null) {
            data.close();
            assertTrue(data.awaitClosed(10, TimeUnit.SECONDS));
        }
        if (isolatedContext != null) {
            File database = isolatedContext.getDatabasePath(CampusDatabase.NAME);
            SQLiteDatabase.deleteDatabase(database);
            File parent = database.getParentFile();
            if (parent != null) parent.delete();
        }
    }

    @Test public void scheduledReminderPostsANotificationAndMarksSent() throws Exception {
        ReminderView scheduled = await(engagement.reminderApi().set(EVENT_ID, 30));
        assertEquals(ReminderStatus.PENDING, scheduled.status);

        Notification delivered = awaitNotification(30_000L);
        assertNotNull(delivered);
        assertEquals(event.title,
                delivered.extras.getString(Notification.EXTRA_TITLE));
        ReminderView sent = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(ReminderStatus.SENT, sent.status);
    }

    private Notification awaitNotification(long timeoutMillis) throws Exception {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            android.service.notification.StatusBarNotification[] active =
                    notificationManager.getActiveNotifications();
            if (active.length > 0) return active[0].getNotification();
            Thread.sleep(100);
        }
        return null;
    }

    private void grantNotificationPermission() throws Exception {
        if (Build.VERSION.SDK_INT < 33) return;
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(),
                        Manifest.permission.POST_NOTIFICATIONS);
    }

    private void grantExactAlarmPermission() throws Exception {
        if (Build.VERSION.SDK_INT < 31) return;
        AlarmManager alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager.canScheduleExactAlarms()) return;
        ParcelFileDescriptor descriptor = InstrumentationRegistry.getInstrumentation()
                .getUiAutomation().executeShellCommand(
                        "appops set " + context.getPackageName()
                                + " SCHEDULE_EXACT_ALARM allow");
        try (InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            while (input.read() != -1) {
                // Drain shell output so the operation completes before scheduling.
            }
        }
        long deadline = System.currentTimeMillis() + 5_000L;
        while (!alarmManager.canScheduleExactAlarms()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertTrue("Exact alarm access was not granted", alarmManager.canScheduleExactAlarms());
    }

    private static <T> T await(AsyncCall<T> call) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        call.enqueue(value -> {
            result.set(value);
            completed.countDown();
        });
        assertTrue("API callback timed out", completed.await(10, TimeUnit.SECONDS));
        assertTrue(result.get().code + " " + result.get().message, result.get().success);
        return result.get().data;
    }

    private final class FixtureEvents implements EventReadPort {
        @Override public EventRecord require(ReadScope scope, long eventId) {
            if (eventId != EVENT_ID) {
                throw new BusinessException(
                        com.example.campusevents.contract.common.ResultCode.EVENT_NOT_FOUND,
                        "missing");
            }
            return event;
        }

        @Override public List<EventRecord> list(ReadScope scope, EventFilter filter) {
            return Collections.singletonList(event);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final File directory;

        IsolatedDatabaseContext(Context base) {
            super(base);
            directory = new File(base.getCacheDir(),
                    "android-reminder-tests-" + UUID.randomUUID());
            if (!directory.mkdirs()) {
                throw new IllegalStateException("Could not create isolated test directory");
            }
        }

        @Override public Context getApplicationContext() {
            return this;
        }

        @Override public File getDatabasePath(String name) {
            return new File(directory, name);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(
                String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler errorHandler) {
            return SQLiteDatabase.openOrCreateDatabase(
                    getDatabasePath(name).getPath(), factory, errorHandler);
        }
    }
}
