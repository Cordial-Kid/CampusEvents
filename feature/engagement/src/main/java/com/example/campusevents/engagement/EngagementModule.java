package com.example.campusevents.engagement;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.engagement.DeliveryState;
import com.example.campusevents.contract.engagement.FavoriteApi;
import com.example.campusevents.contract.engagement.FavoriteItem;
import com.example.campusevents.contract.engagement.ReminderApi;
import com.example.campusevents.contract.engagement.ReminderStatus;
import com.example.campusevents.contract.engagement.ReminderView;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.UserSnapshot;
import com.example.campusevents.platform.data.BusinessException;
import com.example.campusevents.platform.data.DataAccess;
import com.example.campusevents.platform.data.ReadScope;
import com.example.campusevents.platform.data.TxScope;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.EffectContext;
import com.example.campusevents.platform.effect.EffectHandler;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.effect.EffectOutcome;
import com.example.campusevents.platform.port.EngagementReadPort;
import com.example.campusevents.platform.port.EventReadPort;
import com.example.campusevents.platform.port.ReminderEventPort;
import com.example.campusevents.platform.ui.NotificationRouteFactory;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/** D's real implementation of favorites, reminders, scheduling and recovery. */
public final class EngagementModule {
    private final DataAccess data;
    private final EventReadPort events;
    private final EngagementSystem system;
    private final EngagementStore store = new EngagementStore();

    public EngagementModule(Context context, DataAccess data, EventReadPort events,
                            NotificationRouteFactory routes) {
        this(data, events,
                new AndroidEngagementSystem(context.getApplicationContext(), routes));
    }

    public EngagementModule(DataAccess data, EventReadPort events, EngagementSystem system) {
        this.data = Objects.requireNonNull(data, "data");
        this.events = Objects.requireNonNull(events, "events");
        this.system = Objects.requireNonNull(system, "system");
        EngagementRuntime.install(this);
    }

    public boolean isReady() {
        return true;
    }

    public FavoriteApi favoriteApi() {
        return new FavoriteApi() {
            @Override public AsyncCall<Boolean> setFavorite(long eventId, boolean desiredState) {
                return data.write(tx -> {
                    long userId = requireStudent(tx);
                    events.require(tx, eventId);
                    return store.setFavorite(tx, userId, eventId, desiredState);
                });
            }

            @Override public AsyncCall<List<FavoriteItem>> listMine() {
                return data.read(scope -> {
                    long userId = requireStudent(scope);
                    return store.favorites(scope, events, userId);
                });
            }
        };
    }

    public ReminderApi reminderApi() {
        return new ReminderApi() {
            @Override public AsyncCall<ReminderView> get(long eventId) {
                return data.read(scope -> {
                    long userId = requireStudent(scope);
                    events.require(scope, eventId);
                    return store.reminder(scope, userId, eventId);
                });
            }

            @Override public AsyncCall<ReminderView> set(long eventId, int leadMinutes) {
                AsyncCall<Void> intent = data.write(tx -> {
                    long userId = requireStudent(tx);
                    EventRecord event = events.require(tx, eventId);
                    if (!ReminderRules.hasValidLead(leadMinutes)) {
                        throw rejection(ResultCode.INVALID_INPUT);
                    }
                    long triggerAt = ReminderRules.triggerAt(event.startAt, leadMinutes);
                    ResultCode rejection = ReminderRules.setRejection(
                            tx.actor(), event, leadMinutes, triggerAt, tx.now());
                    if (rejection != ResultCode.OK) {
                        throw rejection(rejection);
                    }
                    store.setReminder(tx, userId, event, leadMinutes, triggerAt);
                    tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                    return null;
                });
                return compose(intent, ignored -> readReminder(eventId),
                        EngagementModule.this::validateSet);
            }

            @Override public AsyncCall<ReminderView> disable(long eventId) {
                AsyncCall<Void> intent = data.write(tx -> {
                    long userId = requireStudent(tx);
                    events.require(tx, eventId);
                    store.disable(tx, userId, eventId);
                    tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                    return null;
                });
                return compose(intent, ignored -> readReminder(eventId),
                        EngagementModule.this::validateDisable);
            }
        };
    }

    public EngagementReadPort readPort() {
        return (scope, records) -> ModuleValue.ready(
                store.summaries(scope, records, scope.actor()));
    }

    public ReminderEventPort eventPort() {
        return new ReminderEventPort() {
            @Override public void onEventTimeChanged(TxScope tx, EventRecord before,
                                                     EventRecord after) {
                store.onEventTimeChanged(tx, after.id, after);
            }

            @Override public void disablePendingForEvent(TxScope tx, long eventId) {
                store.disablePendingForEvent(tx, eventId);
            }
        };
    }

    public EffectHandler effectHandler() {
        return new EffectHandler() {
            @Override public EffectKey key() {
                return EffectKey.REMINDER_RECONCILE;
            }

            @Override public EffectOutcome reconcile(EffectContext context) {
                return reconcileReminders(context);
            }
        };
    }

    public AsyncCall<Void> requestReconcile() {
        return data.write(tx -> {
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        });
    }

    public static SchemaContributor schema() {
        return new SchemaContributor() {
            @Override public String id() {
                return "engagement";
            }

            @Override public void create(SQLiteDatabase database) {
                database.execSQL("CREATE TABLE favorites ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "userId INTEGER NOT NULL REFERENCES users(id),"
                        + "eventId INTEGER NOT NULL REFERENCES events(id),"
                        + "createdAt INTEGER NOT NULL,"
                        + "UNIQUE(userId,eventId))");
                database.execSQL("CREATE TABLE reminders ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "userId INTEGER NOT NULL REFERENCES users(id),"
                        + "eventId INTEGER NOT NULL REFERENCES events(id),"
                        + "leadMinutes INTEGER NOT NULL CHECK(leadMinutes>0),"
                        + "triggerAt INTEGER NOT NULL,"
                        + "status TEXT NOT NULL CHECK(status IN "
                        + "('PENDING','DISABLED','SENT','EXPIRED')),"
                        + "revision INTEGER NOT NULL DEFAULT 1 CHECK(revision>0),"
                        + "deliveryState TEXT NOT NULL DEFAULT 'PENDING' CHECK(deliveryState IN "
                        + "('PENDING','SCHEDULED','BLOCKED','FAILED','NOT_REQUIRED')),"
                        + "deliveryError TEXT NOT NULL DEFAULT '',"
                        + "scheduleId INTEGER NOT NULL UNIQUE CHECK(scheduleId>0),"
                        + "createdAt INTEGER NOT NULL,"
                        + "updatedAt INTEGER NOT NULL,"
                        + "UNIQUE(userId,eventId))");
                database.execSQL("CREATE INDEX eng_reminder_status "
                        + "ON reminders(status,triggerAt)");
            }

            @Override public void migrate(SQLiteDatabase database,
                                          int oldVersion, int newVersion) {
                if (oldVersion != 1 || newVersion != 2) {
                    throw new IllegalStateException("Unsupported engagement migration");
                }
            }
        };
    }

    /** Alarm receiver entry point; performs the final validation on the serial data queue. */
    public AsyncCall<Boolean> deliverAlarm(AlarmRequest request) {
        AsyncCall<ReminderNotification> prepared = data.write(tx ->
                prepareAlarm(tx, request));
        return compose(prepared, this::postNotification);
    }

    private ReminderNotification prepareAlarm(TxScope tx, AlarmRequest request) {
        UserSnapshot actor = tx.actor();
        if (actor == null || actor.role != Role.STUDENT
                || actor.userId != request.userId
                || tx.sessionEpoch() != request.epoch) {
            return null;
        }
        ReminderRecord reminder = store.findById(tx, request.reminderId);
        if (reminder == null || reminder.userId != request.userId
                || reminder.eventId != request.eventId
                || reminder.status != ReminderStatus.PENDING
                || reminder.revision != request.revision
                || reminder.triggerAt != request.triggerAt) {
            return null;
        }
        if (tx.now() < reminder.triggerAt) {
            return null;
        }
        EventRecord event = events.require(tx, reminder.eventId);
        if (event.status == EventStatus.CANCELLED) {
            store.markStatus(tx, reminder, ReminderStatus.DISABLED,
                    DeliveryState.PENDING, ResultCode.OK, true, null);
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        }
        if (tx.now() >= event.startAt) {
            store.markStatus(tx, reminder, ReminderStatus.EXPIRED,
                    DeliveryState.NOT_REQUIRED, ResultCode.OK, true, null);
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        }
        if (!system.canDeliverNotifications()) {
            store.markDelivery(tx, reminder, DeliveryState.BLOCKED,
                    ResultCode.REMINDER_UNAVAILABLE);
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        }
        store.markStatus(tx, reminder, ReminderStatus.SENT,
                DeliveryState.NOT_REQUIRED, ResultCode.OK, true, null);
        return new ReminderNotification(reminder.scheduleId, reminder.id,
                event.id, reminder.userId, event.title, reminderText(event));
    }

    private AsyncCall<Boolean> postNotification(ReminderNotification notification) {
        if (notification == null) return immediate(true);
        return callback -> {
            try {
                system.post(notification);
                callback.onResult(ApiResult.ok(true));
            } catch (RuntimeException failure) {
                recoverFailedPost(notification).enqueue(result ->
                        callback.onResult(ApiResult.failure(
                                ResultCode.REMINDER_SCHEDULE_FAILED,
                                "通知提交失败，提醒已标记为失败")));
            }
            return () -> {};
        };
    }

    private AsyncCall<Void> recoverFailedPost(ReminderNotification notification) {
        return data.write(tx -> {
            ReminderRecord reminder = store.findById(tx, notification.reminderId);
            if (reminder == null) return null;
            EventRecord event = events.require(tx, reminder.eventId);
            ReminderStatus status = tx.now() >= event.startAt
                    ? ReminderStatus.EXPIRED : ReminderStatus.PENDING;
            store.markStatus(tx, reminder, status, DeliveryState.FAILED,
                    ResultCode.REMINDER_SCHEDULE_FAILED, true, null);
            tx.requestEffect(EffectKey.REMINDER_RECONCILE);
            return null;
        });
    }

    private EffectOutcome reconcileReminders(EffectContext context) {
        ReconcileSnapshot snapshot = context.read(scope -> {
            List<ReminderRecord> reminders = store.allReminders(scope);
            Map<Long, EventRecord> eventMap = new LinkedHashMap<>();
            for (ReminderRecord reminder : reminders) {
                if (!eventMap.containsKey(reminder.eventId)) {
                    eventMap.put(reminder.eventId, events.require(scope, reminder.eventId));
                }
            }
            return new ReconcileSnapshot(scope.actor(), scope.now(), scope.sessionEpoch(),
                    reminders, eventMap, system.canDeliverNotifications(),
                    system.canScheduleExactAlarms());
        });

        List<ReconcileAction> actions = new ArrayList<>();
        for (ReminderRecord reminder : snapshot.reminders) {
            actions.add(decide(snapshot, reminder));
        }

        boolean retry = false;
        for (ReconcileAction action : actions) {
            if (action.schedule != null) {
                try {
                    system.schedule(action.schedule);
                    action.deliveryState = DeliveryState.SCHEDULED;
                    action.deliveryError = ResultCode.OK;
                } catch (RuntimeException failure) {
                    action.deliveryState = DeliveryState.FAILED;
                    action.deliveryError = ResultCode.REMINDER_SCHEDULE_FAILED;
                    retry = true;
                }
                continue;
            }
            try {
                system.cancel(action.reminder.scheduleId);
                if (action.deliveryState == null) {
                    action.deliveryState = DeliveryState.NOT_REQUIRED;
                    action.deliveryError = ResultCode.OK;
                }
            } catch (RuntimeException failure) {
                action.deliveryState = DeliveryState.FAILED;
                action.deliveryError = ResultCode.REMINDER_SCHEDULE_FAILED;
                retry = true;
            }
            if (action.deliveryState == DeliveryState.BLOCKED) retry = true;
        }

        context.write(tx -> {
            for (ReconcileAction action : actions) {
                if (action.status != null && action.status != action.reminder.status) {
                    store.markStatus(tx, action.reminder, action.status,
                            action.deliveryState == null
                                    ? DeliveryState.NOT_REQUIRED : action.deliveryState,
                            action.deliveryError == null
                                    ? ResultCode.OK : action.deliveryError,
                            true, null);
                } else if (action.writeDelivery) {
                    store.markDelivery(tx, action.reminder,
                            action.deliveryState == null
                                    ? DeliveryState.NOT_REQUIRED : action.deliveryState,
                            action.deliveryError == null
                                    ? ResultCode.OK : action.deliveryError);
                }
            }
            return null;
        });
        return retry ? EffectOutcome.RETRY_LATER : EffectOutcome.APPLIED;
    }

    private ReconcileAction decide(ReconcileSnapshot snapshot, ReminderRecord reminder) {
        EventRecord event = snapshot.events.get(reminder.eventId);
        boolean currentUser = snapshot.actor != null
                && snapshot.actor.role == Role.STUDENT
                && snapshot.actor.userId == reminder.userId;
        boolean canSchedule = currentUser
                && ReminderRules.isSchedulable(event, reminder.triggerAt, snapshot.now);
        if (canSchedule) {
            if (!snapshot.notificationsAvailable || !snapshot.exactAlarmsAvailable) {
                ReconcileAction action = new ReconcileAction(reminder, null);
                action.writeDelivery = true;
                action.deliveryState = DeliveryState.BLOCKED;
                action.deliveryError = ResultCode.REMINDER_UNAVAILABLE;
                return action;
            }
            ReconcileAction action = new ReconcileAction(reminder, new ReminderSchedule(
                    reminder.scheduleId, reminder.id, reminder.eventId, reminder.userId,
                    reminder.revision, reminder.triggerAt, snapshot.epoch));
            action.writeDelivery = true;
            return action;
        }

        ReconcileAction action = new ReconcileAction(reminder, null);
        if (currentUser && reminder.status == ReminderStatus.PENDING) {
            action.status = event.status == EventStatus.CANCELLED
                    ? ReminderStatus.DISABLED : ReminderStatus.EXPIRED;
            action.writeDelivery = true;
        } else if (reminder.status != ReminderStatus.PENDING) {
            action.writeDelivery = reminder.deliveryState != DeliveryState.NOT_REQUIRED;
        }
        return action;
    }

    private AsyncCall<ReminderView> readReminder(long eventId) {
        return data.read(scope -> {
            long userId = requireStudent(scope);
            events.require(scope, eventId);
            return store.reminder(scope, userId, eventId);
        });
    }

    private ApiResult<ReminderView> validateSet(ReminderView view) {
        if (!view.configured) {
            return ApiResult.failure(ResultCode.INTERNAL_ERROR, "提醒保存后未读取到配置");
        }
        if (view.status == ReminderStatus.EXPIRED) {
            return ApiResult.failure(ResultCode.REMINDER_TIME_PASSED, "提醒触发时间已过");
        }
        if (view.status != ReminderStatus.PENDING) {
            return ApiResult.failure(ResultCode.REMINDER_SCHEDULE_FAILED,
                    "提醒未处于待发送状态");
        }
        if (view.deliveryState == DeliveryState.SCHEDULED) {
            return ApiResult.ok(view);
        }
        if (view.deliveryState == DeliveryState.BLOCKED) {
            return ApiResult.failure(ResultCode.REMINDER_UNAVAILABLE,
                    "通知或精确定时权限未开启，提醒尚未生效");
        }
        if (view.deliveryState == DeliveryState.FAILED) {
            return ApiResult.failure(ResultCode.REMINDER_SCHEDULE_FAILED,
                    "系统提醒调度失败，请重试");
        }
        return ApiResult.failure(ResultCode.REMINDER_SCHEDULE_FAILED,
                "提醒尚未同步完成，请重试");
    }

    private ApiResult<ReminderView> validateDisable(ReminderView view) {
        if (view.configured && view.deliveryState == DeliveryState.FAILED) {
            return ApiResult.failure(ResultCode.REMINDER_SCHEDULE_FAILED,
                    "系统提醒取消失败，请重试");
        }
        return ApiResult.ok(view);
    }

    private long requireStudent(ReadScope scope) {
        if (scope.actor() == null) {
            throw new BusinessException(ResultCode.NOT_LOGGED_IN, "请先选择学生身份");
        }
        if (scope.actor().role != Role.STUDENT) {
            throw new BusinessException(ResultCode.FORBIDDEN, "收藏和提醒操作仅限学生");
        }
        return scope.actor().userId;
    }

    private BusinessException rejection(ResultCode code) {
        switch (code) {
            case INVALID_INPUT:
                return new BusinessException(code, "提前分钟数必须为正整数");
            case NOT_LOGGED_IN:
                return new BusinessException(code, "请先选择学生身份");
            case FORBIDDEN:
                return new BusinessException(code, "提醒设置仅限学生");
            case EVENT_NOT_FOUND:
                return new BusinessException(code, "活动不存在，请刷新列表");
            case EVENT_CANCELLED:
                return new BusinessException(code, "活动已取消，不能设置提醒");
            case EVENT_STARTED:
                return new BusinessException(code, "活动已经开始，不能设置提醒");
            case REMINDER_TIME_PASSED:
                return new BusinessException(code, "提醒触发时间已过，请选择更短的提前时间");
            default:
                return new BusinessException(code, "提醒设置失败");
        }
    }

    private String reminderText(EventRecord event) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return "将于 " + format.format(new Date(event.startAt))
                + " 在 " + event.location + " 开始";
    }

    private <T> AsyncCall<T> immediate(T value) {
        AtomicBoolean used = new AtomicBoolean();
        return callback -> {
            if (!used.compareAndSet(false, true)) {
                throw new IllegalStateException("AsyncCall instances can only be enqueued once");
            }
            callback.onResult(ApiResult.ok(value));
            return () -> {};
        };
    }

    private <A, B> AsyncCall<B> compose(AsyncCall<A> first,
                                        Function<A, AsyncCall<B>> next) {
        return compose(first, next, ApiResult::ok);
    }

    private <A, B> AsyncCall<B> compose(AsyncCall<A> first,
                                        Function<A, AsyncCall<B>> next,
                                        Function<B, ApiResult<B>> finish) {
        AtomicBoolean enqueued = new AtomicBoolean();
        AtomicBoolean cancelled = new AtomicBoolean();
        AtomicReference<CallHandle> active = new AtomicReference<>();
        CallHandle handle = () -> {
            cancelled.set(true);
            CallHandle current = active.get();
            if (current != null) current.cancelDelivery();
        };
        return callback -> {
            if (!enqueued.compareAndSet(false, true)) {
                throw new IllegalStateException("AsyncCall instances can only be enqueued once");
            }
            CallHandle firstHandle = first.enqueue(result -> {
                if (cancelled.get()) return;
                if (!result.success) {
                    callback.onResult(ApiResult.failure(result.code, result.message));
                    return;
                }
                AsyncCall<B> second;
                try {
                    second = next.apply(result.data);
                } catch (RuntimeException failure) {
                    callback.onResult(ApiResult.failure(ResultCode.INTERNAL_ERROR,
                            "后续读取失败，请重试"));
                    return;
                }
                CallHandle secondHandle = second.enqueue(after -> {
                    if (cancelled.get()) return;
                    if (!after.success) {
                        callback.onResult(ApiResult.failure(after.code, after.message));
                        return;
                    }
                    try {
                        callback.onResult(finish.apply(after.data));
                    } catch (RuntimeException failure) {
                        callback.onResult(ApiResult.failure(ResultCode.INTERNAL_ERROR,
                                "结果转换失败，请重试"));
                    }
                });
                active.set(secondHandle);
            });
            active.compareAndSet(null, firstHandle);
            return handle;
        };
    }

    private static final class ReconcileSnapshot {
        final UserSnapshot actor;
        final long now;
        final long epoch;
        final List<ReminderRecord> reminders;
        final Map<Long, EventRecord> events;
        final boolean notificationsAvailable;
        final boolean exactAlarmsAvailable;

        ReconcileSnapshot(UserSnapshot actor, long now, long epoch,
                          List<ReminderRecord> reminders, Map<Long, EventRecord> events,
                          boolean notificationsAvailable, boolean exactAlarmsAvailable) {
            this.actor = actor;
            this.now = now;
            this.epoch = epoch;
            this.reminders = reminders;
            this.events = events;
            this.notificationsAvailable = notificationsAvailable;
            this.exactAlarmsAvailable = exactAlarmsAvailable;
        }
    }

    private static final class ReconcileAction {
        final ReminderRecord reminder;
        final ReminderSchedule schedule;
        ReminderStatus status;
        boolean writeDelivery;
        DeliveryState deliveryState;
        ResultCode deliveryError;

        ReconcileAction(ReminderRecord reminder, ReminderSchedule schedule) {
            this.reminder = reminder;
            this.schedule = schedule;
        }
    }
}
