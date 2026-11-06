package com.example.campusevents.platform.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Looper;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.EffectContext;
import com.example.campusevents.platform.effect.EffectHandler;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.effect.EffectOutcome;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real SQLite regressions for the infrastructure contract, without feature implementations. */
@RunWith(AndroidJUnit4.class)
public final class PlatformRegressionTest {
    private Context context;
    private SerialDataAccess data;
    private DataInvalidation invalidation;
    private final SchemaContributor schema = new SchemaContributor() {
        @Override public String id() { return "test_records"; }
        @Override public void create(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE test_records (value INTEGER NOT NULL)");
        }
    };

    @Before public void setUp() throws Exception {
        // Library instrumentation runs in its isolated test target application.
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(CampusDatabase.NAME);
        open(null);
        assertTrue(await(data.current()).success);
    }

    @After public void tearDown() throws Exception {
        closeData();
        context.deleteDatabase(CampusDatabase.NAME);
    }

    @Test public void failedCommandRollsBackDataEffectsAndInvalidation() throws Exception {
        long[] before = await(data.read(this::effectVersions)).data;
        AtomicInteger changes = new AtomicInteger();
        DataInvalidation.Subscription subscription = invalidation.subscribe(change -> changes.incrementAndGet());
        ApiResult<Void> failed = await(data.<Void>write(tx -> {
            tx.db().execSQL("INSERT INTO test_records(value) VALUES(7)");
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            tx.invalidate(ChangeTopic.EVENT_CONTENT, 42);
            throw new BusinessException(ResultCode.MODULE_NOT_READY, "Injected missing participant");
        }));
        assertEquals(ResultCode.MODULE_NOT_READY, failed.code);
        assertNull(failed.data);
        assertEquals(Integer.valueOf(0), await(data.read(this::recordCount)).data);
        assertArrayEquals(before, await(data.read(this::effectVersions)).data);
        assertEquals(0, changes.get());
        subscription.unsubscribe();
    }

    @Test public void queuedOldIdentityCommandIsRejectedAndCallbacksRunOnMain() throws Exception {
        SessionSnapshot admin = await(data.switchDemoUser(1)).data;
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        data.read(scope -> {
            entered.countDown();
            waitFor(release);
            return null;
        }).enqueue(result -> {});
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        AtomicReference<ApiResult<SessionSnapshot>> switched = new AtomicReference<>();
        AtomicReference<ApiResult<Integer>> stale = new AtomicReference<>();
        AtomicBoolean onMain = new AtomicBoolean();
        CountDownLatch delivered = new CountDownLatch(2);
        data.switchDemoUser(2).enqueue(result -> {
            switched.set(result);
            delivered.countDown();
        });
        data.write(tx -> {
            tx.db().execSQL("INSERT INTO test_records(value) VALUES(9)");
            return 9;
        }).enqueue(result -> {
            stale.set(result);
            onMain.set(Looper.myLooper() == Looper.getMainLooper());
            delivered.countDown();
        });
        release.countDown();
        assertTrue(delivered.await(10, TimeUnit.SECONDS));
        assertTrue(switched.get().success);
        assertEquals(2, switched.get().data.user.userId);
        assertEquals(admin.epoch + 1, switched.get().data.epoch);
        assertEquals(ResultCode.SESSION_CHANGED, stale.get().code);
        assertNull(stale.get().data);
        assertTrue(onMain.get());
        assertEquals(Integer.valueOf(0), await(data.read(this::recordCount)).data);
    }

    @Test public void missingEffectHandlerKeepsDurableWorkAndRestartAppliesIt() throws Exception {
        ApiResult<Integer> committed = await(data.write(tx -> {
            tx.db().execSQL("INSERT INTO test_records(value) VALUES(11)");
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return 11;
        }));
        assertTrue(committed.success);
        assertEquals(Integer.valueOf(11), committed.data);
        long[] pending = await(data.read(this::effectVersions)).data;
        assertTrue(pending[0] > pending[1]);
        assertTrue(await(data.read(scope -> scope.hasPendingEffect(EffectKey.REMINDER_RECONCILE))).data);
        closeData();
        AtomicInteger attempts = new AtomicInteger();
        open(new EffectHandler() {
            @Override public EffectKey key() { return EffectKey.REMINDER_RECONCILE; }
            @Override public EffectOutcome reconcile(EffectContext effect) {
                assertEquals(Integer.valueOf(1), effect.read(PlatformRegressionTest.this::recordCount));
                attempts.incrementAndGet();
                return EffectOutcome.APPLIED;
            }
        });
        long[] recovered = await(data.read(this::effectVersions)).data;
        assertTrue(recovered[0] >= pending[0]);
        assertEquals(recovered[0], recovered[1]);
        assertEquals(1, attempts.get());
        assertFalse(await(data.read(scope -> scope.hasPendingEffect(EffectKey.REMINDER_RECONCILE))).data);
    }

    private void open(EffectHandler handler) {
        invalidation = new DataInvalidation();
        data = new SerialDataAccess(new CampusDatabase(context, Collections.singletonList(schema)), invalidation);
        if (handler != null) data.registerEffectHandler(handler);
        data.start();
    }

    private void closeData() throws Exception {
        if (data == null) return;
        data.close();
        assertTrue("Database queue did not close", data.awaitClosed(10, TimeUnit.SECONDS));
        data = null;
    }

    private int recordCount(ReadScope scope) {
        try (Cursor cursor = scope.db().rawQuery("SELECT COUNT(*) FROM test_records", null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private long[] effectVersions(ReadScope scope) {
        try (Cursor cursor = scope.db().rawQuery("SELECT requestedVersion,appliedVersion "
                + "FROM system_effects WHERE effectKey='REMINDER_RECONCILE'", null)) {
            assertTrue(cursor.moveToFirst());
            return new long[]{cursor.getLong(0), cursor.getLong(1)};
        }
    }

    private static <T> ApiResult<T> await(AsyncCall<T> call) throws Exception {
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        CountDownLatch delivered = new CountDownLatch(1);
        call.enqueue(value -> { result.set(value); delivered.countDown(); });
        assertTrue("API callback timed out", delivered.await(10, TimeUnit.SECONDS));
        return result.get();
    }

    private static void waitFor(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test queue timeout");
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }
}
