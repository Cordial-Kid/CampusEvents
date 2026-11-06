package com.example.campusevents.platform.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.SessionApi;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.contract.session.UserSnapshot;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.effect.EffectContext;
import com.example.campusevents.platform.effect.EffectHandler;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.effect.EffectOutcome;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** One queue owns every database scope and the bounded post-commit effect pass. */
public final class SerialDataAccess implements DataAccess, SessionApi {
    private final CampusDatabase helper;
    private final DataInvalidation invalidation;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable ->
            new Thread(runnable, "campus-data"));
    private final Map<EffectKey, EffectHandler> handlers = new EnumMap<>(EffectKey.class);
    private volatile SessionSnapshot current = new SessionSnapshot(null, 0);
    private volatile boolean initialized;
    private volatile boolean started;
    private volatile boolean closed;
    private RuntimeException startupFailure;

    public SerialDataAccess(CampusDatabase helper, DataInvalidation invalidation) {
        this.helper = helper;
        this.invalidation = invalidation;
    }

    public synchronized void registerEffectHandler(EffectHandler handler) {
        if (started) throw new IllegalStateException("Register effect handlers before start");
        if (handlers.putIfAbsent(handler.key(), handler) != null) {
            throw new IllegalStateException("Duplicate effect handler " + handler.key());
        }
    }

    public synchronized void start() {
        if (closed) throw new IllegalStateException("Platform closed");
        if (started) return;
        executor.execute(() -> {
            try {
                current = loadSession(helper.getWritableDatabase());
                initialized = true;
                runWrite(tx -> {
                    tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                    return null;
                });
                attemptEffects();
            } catch (RuntimeException failure) {
                startupFailure = failure;
                Log.e("CampusPlatform", "Database startup failed", failure);
            }
        });
        started = true;
    }

    @Override public <T> AsyncCall<T> read(ReadWork<T> work) {
        Objects.requireNonNull(work);
        return call(() -> runRead(work), false, false);
    }

    @Override public <T> AsyncCall<T> write(WriteWork<T> work) {
        Objects.requireNonNull(work);
        return call(() -> runWrite(work), true, false);
    }

    private interface Operation<T> { T execute(); }

    private <T> AsyncCall<T> call(Operation<T> operation, boolean effects, boolean changesSession) {
        AtomicBoolean enqueued = new AtomicBoolean();
        return callback -> {
            Objects.requireNonNull(callback);
            if (!enqueued.compareAndSet(false, true)) {
                throw new IllegalStateException("AsyncCall instances can only be enqueued once");
            }
            AtomicBoolean cancelled = new AtomicBoolean();
            long capturedEpoch = initialized ? current.epoch : -1;
            enqueue(() -> {
                ApiResult<T> result;
                long deliveryEpoch = current.epoch;
                try {
                    if (startupFailure != null) throw startupFailure;
                    if (capturedEpoch >= 0 && capturedEpoch != current.epoch) {
                        throw new BusinessException(ResultCode.SESSION_CHANGED, "身份已变化，请刷新");
                    }
                    T data = operation.execute();
                    deliveryEpoch = current.epoch;
                    if (effects) attemptEffects();
                    result = ApiResult.ok(data);
                } catch (BusinessException failure) {
                    result = ApiResult.failure(failure.code, failure.getMessage());
                } catch (SQLiteException failure) {
                    Log.e("CampusPlatform", "Database operation failed", failure);
                    result = ApiResult.failure(ResultCode.DATABASE_ERROR, "数据操作失败，请重试");
                } catch (RuntimeException failure) {
                    Log.e("CampusPlatform", "Operation failed", failure);
                    result = ApiResult.failure(ResultCode.INTERNAL_ERROR, "操作未能完成，请重试");
                }
                ApiResult<T> completed = result;
                long expectedEpoch = changesSession ? deliveryEpoch
                        : (capturedEpoch < 0 ? deliveryEpoch : capturedEpoch);
                main.post(() -> {
                    if (cancelled.get()) return;
                    if (current.epoch != expectedEpoch) {
                        callback.onResult(ApiResult.failure(ResultCode.SESSION_CHANGED, "身份已变化，请刷新"));
                    } else {
                        callback.onResult(completed);
                    }
                });
            });
            return () -> cancelled.set(true);
        };
    }

    private <T> T runRead(ReadWork<T> work) {
        ReadScope scope = new ReadScope(helper.getWritableDatabase(), current, System.currentTimeMillis());
        try { return work.run(scope); }
        finally { scope.finish(); }
    }

    private <T> T runWrite(WriteWork<T> work) {
        SQLiteDatabase db = helper.getWritableDatabase();
        TxScope tx = new TxScope(db, current, System.currentTimeMillis());
        T result;
        SessionSnapshot nextSession;
        db.beginTransaction();
        try {
            result = work.run(tx);
            nextSession = loadSession(db);
            db.setTransactionSuccessful();
        } finally {
            tx.finish();
            db.endTransaction();
        }
        current = nextSession;
        invalidation.publishCommitted(tx.changes());
        return result;
    }

    @Override public AsyncCall<List<UserSnapshot>> listDemoUsers() {
        return read(scope -> {
            List<UserSnapshot> users = new ArrayList<>();
            try (Cursor cursor = scope.db().rawQuery("SELECT id,displayName,role FROM users ORDER BY id", null)) {
                while (cursor.moveToNext()) users.add(userAt(cursor));
            }
            return Collections.unmodifiableList(users);
        });
    }

    @Override public AsyncCall<SessionSnapshot> current() {
        return read(ReadScope::session);
    }

    @Override public AsyncCall<SessionSnapshot> switchDemoUser(long userId) {
        return call(() -> runWrite(tx -> {
            UserSnapshot user;
            try (Cursor cursor = tx.db().rawQuery("SELECT id,displayName,role FROM users WHERE id=?",
                    new String[]{Long.toString(userId)})) {
                if (!cursor.moveToFirst()) {
                    throw new BusinessException(ResultCode.INVALID_INPUT, "请选择有效的演示身份");
                }
                user = userAt(cursor);
            }
            if (tx.actor() != null && tx.actor().userId == userId) return tx.session();
            long epoch = tx.sessionEpoch() + 1;
            ContentValues values = new ContentValues();
            values.put("currentUserId", userId);
            values.put("epoch", epoch);
            tx.db().update("app_session", values, "singletonId=1", null);
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            tx.invalidate(ChangeTopic.SESSION, 0);
            return new SessionSnapshot(user, epoch);
        }), true, true);
    }

    private static UserSnapshot userAt(Cursor cursor) {
        return new UserSnapshot(cursor.getLong(0), cursor.getString(1), Role.valueOf(cursor.getString(2)));
    }

    private static SessionSnapshot loadSession(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT u.id,u.displayName,u.role,s.epoch "
                + "FROM app_session s LEFT JOIN users u ON u.id=s.currentUserId WHERE s.singletonId=1", null)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("Missing session row");
            return new SessionSnapshot(cursor.isNull(0) ? null : userAt(cursor), cursor.getLong(3));
        }
    }

    /** Wake once after permission changes, boot recovery, or an explicit user retry. */
    public void retryEffects() {
        enqueue(() -> {
            if (startupFailure == null) attemptEffects();
        });
    }

    private synchronized void enqueue(Runnable task) {
        if (!started || closed) throw new IllegalStateException("Platform must be running");
        executor.execute(task);
    }

    private void attemptEffects() {
        try { drainEffects(); }
        catch (RuntimeException failure) {
            // Business changes are already committed. Pending rows survive for the next wake-up.
            Log.e("CampusPlatform", "Post-commit effect pass failed", failure);
        }
    }

    private void drainEffects() {
        SQLiteDatabase db = helper.getWritableDatabase();
        List<PendingEffect> pending = new ArrayList<>();
        try (Cursor cursor = db.rawQuery("SELECT effectKey,requestedVersion FROM system_effects "
                + "WHERE requestedVersion>appliedVersion ORDER BY effectKey", null)) {
            while (cursor.moveToNext()) {
                pending.add(new PendingEffect(EffectKey.valueOf(cursor.getString(0)), cursor.getLong(1)));
            }
        }
        for (PendingEffect effect : pending) applyEffect(db, effect);
    }

    private void applyEffect(SQLiteDatabase db, PendingEffect effect) {
        EffectHandler handler = handlers.get(effect.key);
        EffectOutcome outcome = EffectOutcome.MODULE_NOT_READY;
        String error = ResultCode.MODULE_NOT_READY.name();
        if (handler != null) {
            ScopedEffectContext context = new ScopedEffectContext(effect.version);
            try {
                outcome = Objects.requireNonNull(handler.reconcile(context));
                error = outcome == EffectOutcome.APPLIED ? "" : outcome.name();
            } catch (RuntimeException failure) {
                Log.e("CampusPlatform", "Effect failed " + effect.key, failure);
                outcome = EffectOutcome.RETRY_LATER;
                error = ResultCode.INTERNAL_ERROR.name();
            } finally {
                context.active = false;
            }
        }
        if (outcome == EffectOutcome.APPLIED) {
            db.execSQL("UPDATE system_effects SET appliedVersion=MAX(appliedVersion,?), "
                    + "attempts=0,lastError='',updatedAt=? WHERE effectKey=?",
                    new Object[]{effect.version,System.currentTimeMillis(),effect.key.name()});
        } else {
            db.execSQL("UPDATE system_effects SET attempts=attempts+1,lastError=?,updatedAt=? WHERE effectKey=?",
                    new Object[]{error,System.currentTimeMillis(),effect.key.name()});
        }
    }

    public synchronized void close() {
        if (closed) return;
        closed = true;
        executor.execute(helper::close);
        executor.shutdown();
    }

    boolean awaitClosed(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    private final class ScopedEffectContext implements EffectContext {
        private final long version;
        private final Thread owner = Thread.currentThread();
        private boolean active = true;
        ScopedEffectContext(long version) { this.version = version; }
        private void verifyActive() {
            if (!active || Thread.currentThread() != owner) {
                throw new IllegalStateException("Effect context escaped reconciliation");
            }
        }
        @Override public long requestedVersion() { verifyActive(); return version; }
        @Override public <T> T read(ReadWork<T> work) { verifyActive(); return runRead(work); }
        @Override public <T> T write(WriteWork<T> work) { verifyActive(); return runWrite(work); }
    }

    private static final class PendingEffect {
        final EffectKey key;
        final long version;
        PendingEffect(EffectKey key, long version) { this.key = key; this.version = version; }
    }
}
