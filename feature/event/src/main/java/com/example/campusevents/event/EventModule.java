package com.example.campusevents.event;

import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventAdminApi;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventInput;
import com.example.campusevents.contract.event.EventReadApi;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.event.data.EventSchema;
import com.example.campusevents.event.data.EventStore;
import com.example.campusevents.platform.data.BusinessException;
import com.example.campusevents.platform.data.DataAccess;
import com.example.campusevents.platform.data.ReadScope;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.port.EventReadPort;
import com.example.campusevents.platform.port.RegistrationEventPort;
import com.example.campusevents.platform.port.ReminderEventPort;

import java.net.URI;
import java.util.List;
import java.util.Objects;

/** Two-stage assembly keeps activity reads independent from registration and reminders. */
public final class EventModule {
    private final DataAccess dataAccess;
    private final EventStore store = new EventStore();
    private final EventReadApi readApi;

    public EventModule(DataAccess dataAccess) {
        this.dataAccess = Objects.requireNonNull(dataAccess);
        readApi = new EventReadApi() {
            @Override public AsyncCall<List<EventRecord>> list(EventFilter filter) {
                return dataAccess.read(scope -> store.list(scope, filter));
            }

            @Override public AsyncCall<EventRecord> get(long eventId) {
                return dataAccess.read(scope -> store.require(scope, eventId));
            }
        };
    }

    public static SchemaContributor schema() { return new EventSchema(); }
    public EventReadApi readApi() { return readApi; }
    public EventReadPort readPort() { return store; }

    public EventAdminApi createAdmin(RegistrationEventPort registration, ReminderEventPort reminders) {
        Objects.requireNonNull(registration);
        Objects.requireNonNull(reminders);
        return new EventAdminApi() {
            @Override public AsyncCall<EventRecord> create(EventInput input) {
                return dataAccess.write(tx -> {
                    requireAdmin(tx);
                    validate(input, tx.now());
                    long id = store.insert(tx.db(), input, tx.actor().userId, tx.now());
                    tx.invalidate(ChangeTopic.EVENT_CONTENT, id);
                    return store.require(tx, id);
                });
            }

            @Override public AsyncCall<EventRecord> update(long eventId, long expectedVersion, EventInput input) {
                return dataAccess.write(tx -> {
                    requireAdmin(tx);
                    EventRecord before = store.require(tx, eventId);
                    requireEditable(before, expectedVersion, tx.now());
                    validate(input, tx.now());
                    if (input.capacity != before.capacity
                            && input.capacity < registration.countActive(tx, eventId)) {
                        throw new BusinessException(ResultCode.CAPACITY_TOO_SMALL, "新名额不能小于当前有效报名人数");
                    }
                    store.update(tx.db(), before, input, tx.now());
                    EventRecord after = store.require(tx, eventId);
                    if (before.startAt != after.startAt || before.endAt != after.endAt) {
                        reminders.onEventTimeChanged(tx, before, after);
                        tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                    }
                    tx.invalidate(ChangeTopic.EVENT_CONTENT, eventId);
                    return after;
                });
            }

            @Override public AsyncCall<EventRecord> cancel(long eventId, long expectedVersion) {
                return dataAccess.write(tx -> {
                    requireAdmin(tx);
                    EventRecord before = store.require(tx, eventId);
                    if (before.status == EventStatus.CANCELLED) return before;
                    requireEditable(before, expectedVersion, tx.now());
                    store.cancel(tx.db(), before, tx.now());
                    registration.cancelActiveForEvent(tx, eventId);
                    reminders.disablePendingForEvent(tx, eventId);
                    tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                    tx.invalidate(ChangeTopic.EVENT_CONTENT, eventId);
                    tx.invalidate(ChangeTopic.REGISTRATION, eventId);
                    tx.invalidate(ChangeTopic.ENGAGEMENT, eventId);
                    return store.require(tx, eventId);
                });
            }
        };
    }

    private void requireAdmin(ReadScope scope) {
        if (scope.actor() == null) throw new BusinessException(ResultCode.NOT_LOGGED_IN, "请先选择演示身份");
        if (scope.actor().role != Role.ADMIN) throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以维护活动");
    }

    private void requireEditable(EventRecord event, long expectedVersion, long now) {
        if (event.status == EventStatus.CANCELLED) throw new BusinessException(ResultCode.EVENT_CANCELLED, "已取消的活动不能编辑");
        if (event.version != expectedVersion) throw new BusinessException(ResultCode.STALE_DATA, "活动已被修改，请刷新后重试");
        if (event.startAt <= now) throw new BusinessException(ResultCode.EVENT_STARTED, "活动已经开始，不能编辑或取消");
    }

    private void validate(EventInput input, long now) {
        if (input == null || blank(input.title) || blank(input.description) || blank(input.location)
                || blank(input.category) || input.sourceType == null || blank(input.sourceName)
                || input.sourceUrl == null) {
            throw new BusinessException(ResultCode.INVALID_INPUT, "请填写标题、介绍、地点、分类和来源说明");
        }
        if (input.title.trim().length() > 100 || input.location.trim().length() > 200
                || input.description.length() > 10000 || input.category.length() > 50
                || input.sourceName.length() > 200 || input.sourceUrl.length() > 2000) {
            throw new BusinessException(ResultCode.INVALID_INPUT, "表单内容过长，请缩短后重试");
        }
        if (input.capacity <= 0 || input.registrationDeadline <= 0
                || input.registrationDeadline > input.startAt || input.startAt <= now || input.endAt <= input.startAt) {
            throw new BusinessException(ResultCode.INVALID_INPUT, "名额须为正整数，截止时间不晚于开始时间，开始时间须在未来且早于结束时间");
        }
        if ((input.sourceType == SourceType.OFFICIAL || !input.sourceUrl.trim().isEmpty())
                && !validWebUrl(input.sourceUrl.trim())) {
            throw new BusinessException(ResultCode.INVALID_INPUT, "来源链接须为有效的 http 或 https 网址，官方来源必须填写链接");
        }
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private boolean validWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && !uri.getHost().isEmpty();
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
