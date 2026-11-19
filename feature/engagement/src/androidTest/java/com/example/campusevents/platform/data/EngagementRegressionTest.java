package com.example.campusevents.platform.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.engagement.DeliveryState;
import com.example.campusevents.contract.engagement.ReminderStatus;
import com.example.campusevents.contract.engagement.ReminderView;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.engagement.AlarmRequest;
import com.example.campusevents.engagement.EngagementModule;
import com.example.campusevents.engagement.EngagementSystem;
import com.example.campusevents.engagement.ReminderNotification;
import com.example.campusevents.engagement.ReminderSchedule;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.port.EventReadPort;
import java.io.File;
import java.util.ArrayList;
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

/** Real SQLite through production D code; A's event module is replaced by the stable read port. */
@RunWith(AndroidJUnit4.class)
public final class EngagementRegressionTest {
    private static final long EVENT_ID = 1L;

    private Context isolatedContext;
    private SerialDataAccess data;
    private EngagementModule engagement;
    private FakeSystem system;
    private volatile EventRecord currentEvent;
    private List<SchemaContributor> schemas;

    @Before public void setUp() throws Exception {
        Context base = InstrumentationRegistry.getInstrumentation().getTargetContext();
        isolatedContext = new IsolatedDatabaseContext(base);
        long startAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2);
        currentEvent = event(startAt, EventStatus.ACTIVE, 1);
        SchemaContributor eventFixture = new SchemaContributor() {
            @Override public String id() {
                return "engagement-event-fixture";
            }

            @Override public void create(SQLiteDatabase database) {
                database.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY)");
                database.execSQL("INSERT INTO events VALUES(1)");
            }
        };
        schemas = Arrays.asList(eventFixture, EngagementModule.schema());
        data = new SerialDataAccess(new CampusDatabase(isolatedContext, schemas),
                new DataInvalidation());
        system = new FakeSystem();
        engagement = new EngagementModule(data, new FixtureEvents(), system);
        data.registerEffectHandler(engagement.effectHandler());
        data.start();
        assertNotNull(await(data.current()));
    }

    @After public void tearDown() throws Exception {
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

    @Test public void favoritesUseTargetStateAndSeparateUsers() throws Exception {
        switchTo(2);
        assertTrue(await(engagement.favoriteApi().setFavorite(EVENT_ID, true)));
        assertTrue(await(engagement.favoriteApi().setFavorite(EVENT_ID, true)));
        assertEquals(1, await(engagement.favoriteApi().listMine()).size());

        switchTo(3);
        assertTrue(await(engagement.favoriteApi().listMine()).isEmpty());
        switchTo(1);
        assertEquals(ResultCode.FORBIDDEN,
                awaitResult(engagement.favoriteApi().setFavorite(EVENT_ID, true)).code);

        switchTo(2);
        assertFalse(await(engagement.favoriteApi().setFavorite(EVENT_ID, false)));
        assertFalse(await(engagement.favoriteApi().setFavorite(EVENT_ID, false)));
        assertTrue(await(engagement.favoriteApi().listMine()).isEmpty());
    }

    @Test public void reminderSetWaitsForSchedulingAndPreservesOldIntentOnRejection()
            throws Exception {
        switchTo(2);
        ReminderView configured = await(engagement.reminderApi().set(EVENT_ID, 30));
        assertEquals(ReminderStatus.PENDING, configured.status);
        assertEquals(DeliveryState.SCHEDULED, configured.deliveryState);
        assertEquals(1, system.scheduled.size());
        ReminderSchedule schedule = system.scheduled.get(0);
        assertEquals(currentEvent.startAt - TimeUnit.MINUTES.toMillis(30), schedule.triggerAt);
        assertTrue(schedule.epoch > 0);

        ApiResult<ReminderView> rejected =
                awaitResult(engagement.reminderApi().set(EVENT_ID, 3_000));
        assertEquals(ResultCode.REMINDER_TIME_PASSED, rejected.code);
        ReminderView unchanged = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(Integer.valueOf(30), unchanged.leadMinutes);
        assertEquals(configured.triggerAt, unchanged.triggerAt);

        assertEquals(ResultCode.INVALID_INPUT,
                awaitResult(engagement.reminderApi().set(EVENT_ID, 0)).code);

        switchTo(3);
        assertFalse(await(engagement.reminderApi().get(EVENT_ID)).configured);
        switchTo(2);
        assertTrue(await(engagement.reminderApi().get(EVENT_ID)).configured);
    }

    @Test public void permissionsBlockDeliveryAndExplicitRetrySchedulesTheSameIntent()
            throws Exception {
        switchTo(2);
        system.notificationsAvailable = false;
        ApiResult<ReminderView> blocked =
                awaitResult(engagement.reminderApi().set(EVENT_ID, 30));
        assertEquals(ResultCode.REMINDER_UNAVAILABLE, blocked.code);
        ReminderView stored = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(DeliveryState.BLOCKED, stored.deliveryState);
        assertEquals(0, system.scheduled.size());

        system.notificationsAvailable = true;
        await(engagement.requestReconcile());
        ReminderView retried = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(ReminderStatus.PENDING, retried.status);
        assertEquals(DeliveryState.SCHEDULED, retried.deliveryState);
        assertEquals(1, system.scheduled.size());
    }

    @Test public void timeChangeReschedulesAndCancellationDisablesWithoutDeletingFavorite()
            throws Exception {
        switchTo(2);
        await(engagement.favoriteApi().setFavorite(EVENT_ID, true));
        await(engagement.reminderApi().set(EVENT_ID, 30));
        long originalRevision = system.scheduled.get(0).revision;

        switchTo(1);
        EventRecord changed = event(currentEvent.startAt + TimeUnit.DAYS.toMillis(1),
                EventStatus.ACTIVE, currentEvent.version + 1);
        await(applyEventChange(changed, false));

        switchTo(2);
        assertEquals(2, system.scheduled.size());
        ReminderSchedule rescheduled = system.scheduled.get(1);
        assertEquals(changed.startAt - TimeUnit.MINUTES.toMillis(30), rescheduled.triggerAt);
        assertEquals(originalRevision + 1, rescheduled.revision);

        switchTo(1);
        EventRecord cancelled = event(changed.startAt, EventStatus.CANCELLED,
                changed.version + 1);
        await(applyEventChange(cancelled, true));
        assertTrue(system.canceled.contains(rescheduled.scheduleId));

        switchTo(2);
        ReminderView disabled = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(ReminderStatus.DISABLED, disabled.status);
        assertEquals(DeliveryState.NOT_REQUIRED, disabled.deliveryState);
        assertEquals(1, await(engagement.favoriteApi().listMine()).size());
        assertEquals(changed.startAt - TimeUnit.MINUTES.toMillis(30),
                disabled.triggerAt.longValue());
    }

    @Test public void alarmRequiresMatchingEpochRevisionAndTimeBeforePosting() throws Exception {
        switchTo(2);
        await(engagement.reminderApi().set(EVENT_ID, 30));
        ReminderRow row = reminderRow();
        SessionSnapshot session = await(data.current());

        await(data.write(tx -> {
            tx.db().execSQL("UPDATE reminders SET triggerAt=0 WHERE id=?",
                    new Object[]{row.id});
            return null;
        }));

        await(engagement.deliverAlarm(new AlarmRequest(row.id, EVENT_ID, 2,
                row.revision - 1, 0, session.epoch)));
        assertEquals(0, system.posted.size());
        assertEquals(ReminderStatus.PENDING,
                await(engagement.reminderApi().get(EVENT_ID)).status);

        assertTrue(await(engagement.deliverAlarm(new AlarmRequest(row.id, EVENT_ID, 2,
                row.revision, 0, session.epoch))));
        assertEquals(1, system.posted.size());
        assertEquals(ReminderStatus.SENT,
                await(engagement.reminderApi().get(EVENT_ID)).status);
    }

    @Test public void processRestartReconcilesPersistedPendingReminder() throws Exception {
        switchTo(2);
        ReminderView beforeRestart = await(engagement.reminderApi().set(EVENT_ID, 30));
        assertEquals(DeliveryState.SCHEDULED, beforeRestart.deliveryState);
        assertEquals(1, system.scheduled.size());

        data.close();
        assertTrue(data.awaitClosed(10, TimeUnit.SECONDS));
        system = new FakeSystem();
        data = new SerialDataAccess(new CampusDatabase(isolatedContext, schemas),
                new DataInvalidation());
        engagement = new EngagementModule(data, new FixtureEvents(), system);
        data.registerEffectHandler(engagement.effectHandler());
        data.start();
        assertNotNull(await(data.current()));

        assertEquals(1, system.scheduled.size());
        assertEquals(beforeRestart.triggerAt,
                Long.valueOf(system.scheduled.get(0).triggerAt));
        ReminderView recovered = await(engagement.reminderApi().get(EVENT_ID));
        assertEquals(ReminderStatus.PENDING, recovered.status);
        assertEquals(DeliveryState.SCHEDULED, recovered.deliveryState);
    }

    private AsyncCall<Void> applyEventChange(EventRecord after, boolean cancelled) {
        EventRecord before = currentEvent;
        currentEvent = after;
        return data.write(tx -> {
            if (cancelled) {
                engagement.eventPort().disablePendingForEvent(tx, EVENT_ID);
            } else {
                engagement.eventPort().onEventTimeChanged(tx, before, after);
            }
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        });
    }

    private EventRecord event(long startAt, EventStatus status, long version) {
        return new EventRecord(EVENT_ID, "D 回归活动", "提醒与收藏回归测试", "测试教室",
                startAt, startAt + TimeUnit.HOURS.toMillis(2),
                startAt - TimeUnit.HOURS.toMillis(1), 20, "TECH",
                SourceType.MANUAL, "自动化测试", "", status, 1, 0, 0, version);
    }

    private void switchTo(long userId) throws Exception {
        await(data.switchDemoUser(userId));
        SessionSnapshot session = await(data.current());
        assertNotNull(session.user);
        assertEquals(userId, session.user.userId);
        assertEquals(userId == 1 ? Role.ADMIN : Role.STUDENT, session.user.role);
    }

    private ReminderRow reminderRow() throws Exception {
        return await(data.read(scope -> {
            try (Cursor cursor = scope.db().rawQuery(
                    "SELECT id,revision FROM reminders WHERE eventId=?",
                    new String[]{Long.toString(EVENT_ID)})) {
                assertTrue(cursor.moveToFirst());
                return new ReminderRow(cursor.getLong(0), cursor.getLong(1));
            }
        }));
    }

    private static <T> T await(AsyncCall<T> call) throws Exception {
        ApiResult<T> result = awaitResult(call);
        assertTrue(result.code + " " + result.message, result.success);
        return result.data;
    }

    private static <T> ApiResult<T> awaitResult(AsyncCall<T> call) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        call.enqueue(value -> {
            result.set(value);
            completed.countDown();
        });
        assertTrue("API callback timed out", completed.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    private final class FixtureEvents implements EventReadPort {
        @Override public EventRecord require(ReadScope scope, long eventId) {
            if (eventId != EVENT_ID) {
                throw new BusinessException(ResultCode.EVENT_NOT_FOUND, "missing");
            }
            return currentEvent;
        }

        @Override public List<EventRecord> list(ReadScope scope, EventFilter filter) {
            return Collections.singletonList(currentEvent);
        }
    }

    private static final class ReminderRow {
        final long id;
        final long revision;

        ReminderRow(long id, long revision) {
            this.id = id;
            this.revision = revision;
        }
    }

    private static final class FakeSystem implements EngagementSystem {
        boolean notificationsAvailable = true;
        boolean exactAlarmsAvailable = true;
        final List<ReminderSchedule> scheduled = new ArrayList<>();
        final List<Integer> canceled = new ArrayList<>();
        final List<ReminderNotification> posted = new ArrayList<>();

        @Override public boolean canDeliverNotifications() {
            return notificationsAvailable;
        }

        @Override public boolean canScheduleExactAlarms() {
            return exactAlarmsAvailable;
        }

        @Override public void schedule(ReminderSchedule reminder) {
            scheduled.add(reminder);
        }

        @Override public void cancel(int scheduleId) {
            canceled.add(scheduleId);
        }

        @Override public void post(ReminderNotification notification) {
            posted.add(notification);
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final File directory;

        IsolatedDatabaseContext(Context base) {
            super(base);
            directory = new File(base.getCacheDir(),
                    "engagement-tests-" + UUID.randomUUID());
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
