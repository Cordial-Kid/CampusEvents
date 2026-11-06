package com.example.campusevents.platform.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventAdminApi;
import com.example.campusevents.contract.event.EventInput;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.event.EventModule;
import com.example.campusevents.platform.data.BusinessException;
import com.example.campusevents.platform.data.TxScope;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.port.RegistrationEventPort;
import com.example.campusevents.platform.port.ReminderEventPort;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real SQLite transactions, with test-only collaborators injected through stable ports. */
@RunWith(AndroidJUnit4.class)
public final class EventModuleRegressionTest {
    private SerialDataAccess platform;
    private EventModule events;
    private RegistrationStub registration;
    private ReminderStub reminders;
    private EventAdminApi admin;
    private Context isolatedContext;

    @Before public void setUp() throws Exception {
        isolatedContext = new IsolatedDatabaseContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        platform = new SerialDataAccess(new CampusDatabase(isolatedContext, Arrays.asList(EventModule.schema(), new TestSchema())), new DataInvalidation());
        events = new EventModule(platform);
        registration = new RegistrationStub();
        reminders = new ReminderStub();
        admin = events.createAdmin(registration, reminders);
        platform.start();
        assertTrue(await(platform.current()).success);
        assertTrue(await(platform.switchDemoUser(1)).success);
    }

    @After public void tearDown() throws Exception {
        if (platform != null) {
            platform.close();
            assertTrue(platform.awaitClosed(10, TimeUnit.SECONDS));
        }
        if (isolatedContext != null) {
            File database = isolatedContext.getDatabasePath(CampusDatabase.NAME);
            SQLiteDatabase.deleteDatabase(database);
            database.getParentFile().delete();
        }
    }

    @Test public void studentCannotCreateOrUpdate() throws Exception {
        EventRecord event = create();
        assertTrue(await(platform.switchDemoUser(2)).success);
        assertEquals(ResultCode.FORBIDDEN, await(admin.create(input())).code);
        assertEquals(ResultCode.FORBIDDEN, await(admin.update(event.id, event.version, changed(event, "学生修改", event.startAt, event.capacity))).code);
        assertEquals(event.version, get(event.id).version);
    }

    @Test public void contentEditPreservesTimesAndRejectsStaleVersion() throws Exception {
        EventRecord before = create();
        registration.fail = true;
        reminders.failTime = true;
        ApiResult<EventRecord> updated = await(admin.update(before.id, before.version, changed(before, "更新后的标题", before.startAt, before.capacity)));
        assertTrue(updated.message, updated.success);
        assertEquals("更新后的标题", updated.data.title);
        assertEquals(before.startAt, updated.data.startAt);
        assertEquals(before.endAt, updated.data.endAt);
        assertEquals(before.registrationDeadline, updated.data.registrationDeadline);
        assertEquals(before.version + 1, updated.data.version);
        assertEquals(ResultCode.STALE_DATA, await(admin.update(before.id, before.version, changed(before, "过期修改", before.startAt, before.capacity))).code);
        assertEquals("更新后的标题", get(before.id).title);
    }

    @Test public void registrationFailureRollsBackCancellation() throws Exception {
        EventRecord before = create();
        long effectVersion = effectVersion();
        registration.fail = true;
        assertEquals(ResultCode.MODULE_NOT_READY, await(admin.cancel(before.id, before.version)).code);
        assertUnchanged(before);
        assertEquals(0, marker(1));
        assertEquals(0, marker(2));
        assertEquals(effectVersion, effectVersion());
    }

    @Test public void reminderFailureRollsBackEventAndRegistrationChanges() throws Exception {
        EventRecord before = create();
        long effectVersion = effectVersion();
        reminders.failCancel = true;
        assertEquals(ResultCode.MODULE_NOT_READY, await(admin.cancel(before.id, before.version)).code);
        assertUnchanged(before);
        assertEquals(0, marker(1));
        assertEquals(0, marker(2));
        assertEquals(effectVersion, effectVersion());
    }

    @Test public void reminderFailureRollsBackTimeChange() throws Exception {
        EventRecord before = create();
        long effectVersion = effectVersion();
        reminders.failTime = true;
        assertEquals(ResultCode.MODULE_NOT_READY, await(admin.update(before.id, before.version,
                changed(before, before.title, before.startAt + 60_000, before.capacity))).code);
        assertUnchanged(before);
        assertEquals(0, marker(2));
        assertEquals(effectVersion, effectVersion());
    }

    @Test public void capacityCannotBeLowerThanActiveRegistrations() throws Exception {
        EventRecord before = create();
        registration.active = 6;
        assertEquals(ResultCode.CAPACITY_TOO_SMALL, await(admin.update(before.id, before.version,
                changed(before, before.title, before.startAt, 5))).code);
        assertUnchanged(before);
    }

    @Test public void cancellationIsIdempotentAndUsesOneTransaction() throws Exception {
        EventRecord before = create();
        long effectVersion = effectVersion();
        ApiResult<EventRecord> result = await(admin.cancel(before.id, before.version));
        assertTrue(result.message, result.success);
        assertEquals(EventStatus.CANCELLED, result.data.status);
        assertEquals(before.version + 1, result.data.version);
        assertEquals(1, marker(1));
        assertEquals(1, marker(2));
        assertEquals(effectVersion + 1, effectVersion());
        // The old expectedVersion is allowed when the requested terminal state already exists.
        ApiResult<EventRecord> repeated = await(admin.cancel(before.id, before.version));
        assertTrue(repeated.success);
        assertEquals(result.data.version, repeated.data.version);
        assertEquals(effectVersion + 1, effectVersion());
    }

    private EventRecord create() throws Exception {
        ApiResult<EventRecord> result = await(admin.create(input()));
        assertTrue(result.message, result.success);
        return result.data;
    }

    private EventRecord get(long eventId) throws Exception {
        ApiResult<EventRecord> result = await(events.readApi().get(eventId));
        assertTrue(result.message, result.success);
        return result.data;
    }

    private void assertUnchanged(EventRecord before) throws Exception {
        EventRecord after = get(before.id);
        assertEquals(before.version, after.version);
        assertEquals(before.status, after.status);
        assertEquals(before.title, after.title);
        assertEquals(before.startAt, after.startAt);
        assertEquals(before.capacity, after.capacity);
    }

    private EventInput input() {
        long start = System.currentTimeMillis() + 4L * 24 * 60 * 60 * 1000;
        return new EventInput("自动化测试活动", "仅用于回归测试", "测试教室", start,
                start + 2 * 60 * 60 * 1000, start - 60 * 60 * 1000, 8,
                "TECH", SourceType.MANUAL, "自动化回归测试", "");
    }

    private EventInput changed(EventRecord before, String title, long startAt, int capacity) {
        return new EventInput(title, before.description, before.location, startAt, before.endAt,
                before.registrationDeadline, capacity, before.category, before.sourceType,
                before.sourceName, before.sourceUrl);
    }

    private int marker(int id) throws Exception {
        return await(platform.read(scope -> {
            try (Cursor cursor = scope.db().rawQuery("SELECT value FROM event_test_markers WHERE id=?", new String[]{Integer.toString(id)})) {
                assertTrue(cursor.moveToFirst());
                return cursor.getInt(0);
            }
        })).data;
    }

    private long effectVersion() throws Exception {
        return await(platform.read(scope -> {
            try (Cursor cursor = scope.db().rawQuery("SELECT requestedVersion FROM system_effects WHERE effectKey='REMINDER_RECONCILE'", null)) {
                assertTrue(cursor.moveToFirst());
                return cursor.getLong(0);
            }
        })).data;
    }

    private <T> ApiResult<T> await(AsyncCall<T> call) throws Exception {
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        AtomicBoolean onMain = new AtomicBoolean();
        call.enqueue(value -> {
            result.set(value);
            onMain.set(Looper.myLooper() == Looper.getMainLooper());
            delivered.countDown();
        });
        assertTrue("Result timed out", delivered.await(10, TimeUnit.SECONDS));
        assertTrue("Callbacks must run on the main thread", onMain.get());
        assertNotNull(result.get());
        return result.get();
    }

    private static final class RegistrationStub implements RegistrationEventPort {
        boolean fail;
        int active;
        @Override public int countActive(TxScope tx, long eventId) {
            if (fail) throw unavailable();
            return active;
        }
        @Override public void cancelActiveForEvent(TxScope tx, long eventId) {
            tx.db().execSQL("UPDATE event_test_markers SET value=value+1 WHERE id=1");
            if (fail) throw unavailable();
        }
    }

    private static final class ReminderStub implements ReminderEventPort {
        boolean failTime;
        boolean failCancel;
        @Override public void onEventTimeChanged(TxScope tx, EventRecord before, EventRecord after) {
            tx.db().execSQL("UPDATE event_test_markers SET value=value+1 WHERE id=2");
            if (failTime) throw unavailable();
        }
        @Override public void disablePendingForEvent(TxScope tx, long eventId) {
            tx.db().execSQL("UPDATE event_test_markers SET value=value+1 WHERE id=2");
            if (failCancel) throw unavailable();
        }
    }

    private static BusinessException unavailable() {
        return new BusinessException(ResultCode.MODULE_NOT_READY, "Injected collaborator failure");
    }

    private static final class TestSchema implements SchemaContributor {
        @Override public String id() { return "event-test-markers"; }
        @Override public void create(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE event_test_markers(id INTEGER PRIMARY KEY,value INTEGER NOT NULL)");
            db.execSQL("INSERT INTO event_test_markers VALUES(1,0),(2,0)");
        }
    }

    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final File directory;
        IsolatedDatabaseContext(Context base) {
            super(base);
            directory = new File(base.getCacheDir(), "event-tests-" + UUID.randomUUID());
            if (!directory.mkdirs()) throw new IllegalStateException("Could not create isolated test directory");
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getDatabasePath(String name) { return new File(directory, name); }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, errorHandler);
        }
    }
}
