package com.example.campusevents.engagement;

import android.content.ContentValues;
import android.database.Cursor;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.engagement.DeliveryState;
import com.example.campusevents.contract.engagement.EngagementSummary;
import com.example.campusevents.contract.engagement.FavoriteItem;
import com.example.campusevents.contract.engagement.ReminderStatus;
import com.example.campusevents.contract.engagement.ReminderView;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.UserSnapshot;
import com.example.campusevents.platform.data.BusinessException;
import com.example.campusevents.platform.data.ReadScope;
import com.example.campusevents.platform.data.TxScope;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.port.EventReadPort;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** D's SQL stays in this class; other modules only use APIs and ports. */
final class EngagementStore {
    boolean setFavorite(TxScope tx, long userId, long eventId, boolean desiredState) {
        boolean changed;
        if (desiredState) {
            ContentValues values = new ContentValues();
            values.put("userId", userId);
            values.put("eventId", eventId);
            values.put("createdAt", tx.now());
            changed = tx.db().insertWithOnConflict(
                    "favorites", null, values, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE) > 0;
        } else {
            changed = tx.db().delete("favorites", "userId=? AND eventId=?",
                    new String[]{Long.toString(userId), Long.toString(eventId)}) > 0;
        }
        if (changed) tx.invalidate(ChangeTopic.ENGAGEMENT, eventId);
        return desiredState;
    }

    List<FavoriteItem> favorites(ReadScope scope, EventReadPort events, long userId) {
        List<FavoriteItem> result = new ArrayList<>();
        try (Cursor cursor = scope.db().rawQuery(
                "SELECT eventId,createdAt FROM favorites WHERE userId=? "
                        + "ORDER BY createdAt DESC,id DESC",
                new String[]{Long.toString(userId)})) {
            while (cursor.moveToNext()) {
                EventRecord event = events.require(scope, cursor.getLong(0));
                result.add(new FavoriteItem(event, cursor.getLong(1)));
            }
        }
        return Collections.unmodifiableList(result);
    }

    Map<Long, EngagementSummary> summaries(ReadScope scope,
                                           List<EventRecord> records, UserSnapshot actor) {
        if (records.isEmpty()) return Collections.emptyMap();
        Set<Long> favoriteEvents = Collections.emptySet();
        Map<Long, ReminderRecord> reminders = Collections.emptyMap();
        if (actor != null && Role.STUDENT == actor.role) {
            favoriteEvents = favoriteEvents(scope, actor.userId);
            reminders = remindersByEvent(scope, actor.userId);
        }
        boolean pendingEffect = scope.hasPendingEffect(EffectKey.REMINDER_RECONCILE);
        Map<Long, EngagementSummary> result = new LinkedHashMap<>();
        for (EventRecord event : records) {
            ReminderRecord reminder = reminders.get(event.id);
            ReminderView view = reminder == null
                    ? ReminderView.unconfigured()
                    : view(reminder, pendingEffect);
            result.put(event.id,
                    new EngagementSummary(favoriteEvents.contains(event.id), view));
        }
        return Collections.unmodifiableMap(result);
    }

    ReminderView reminder(ReadScope scope, long userId, long eventId) {
        ReminderRecord record = findByUserEvent(scope, userId, eventId);
        return record == null ? ReminderView.unconfigured()
                : view(record, scope.hasPendingEffect(EffectKey.REMINDER_RECONCILE));
    }

    ReminderView setReminder(TxScope tx, long userId, EventRecord event,
                             int leadMinutes, long triggerAt) {
        ReminderRecord before = findByUserEvent(tx, userId, event.id);
        ContentValues values = new ContentValues();
        values.put("leadMinutes", leadMinutes);
        values.put("triggerAt", triggerAt);
        values.put("status", ReminderStatus.PENDING.name());
        values.put("deliveryState", DeliveryState.PENDING.name());
        values.put("deliveryError", "");
        values.put("updatedAt", tx.now());
        long revision;
        if (before == null) {
            int scheduleId = nextScheduleId(tx);
            values.put("userId", userId);
            values.put("eventId", event.id);
            values.put("revision", 1L);
            values.put("scheduleId", scheduleId);
            values.put("createdAt", tx.now());
            revision = 1L;
            tx.db().insertOrThrow("reminders", null, values);
        } else {
            values.put("revision", before.revision + 1);
            revision = before.revision + 1;
            int changed = tx.db().update("reminders", values, "id=? AND revision=?",
                    new String[]{Long.toString(before.id), Long.toString(before.revision)});
            if (changed != 1) {
                throw new BusinessException(ResultCode.REMINDER_SCHEDULE_FAILED,
                        "提醒已被修改，请重新设置");
            }
        }
        tx.invalidate(ChangeTopic.ENGAGEMENT, event.id);
        ReminderRecord after = findByUserEvent(tx, userId, event.id);
        if (after == null || after.revision != revision) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "提醒保存后无法读取");
        }
        return view(after, false);
    }

    ReminderView disable(TxScope tx, long userId, long eventId) {
        ReminderRecord before = findByUserEvent(tx, userId, eventId);
        if (before == null) return ReminderView.unconfigured();
        if (before.status != ReminderStatus.PENDING) {
            return view(before, tx.hasPendingEffect(EffectKey.REMINDER_RECONCILE));
        }
        updateStatus(tx, before, ReminderStatus.DISABLED, DeliveryState.PENDING,
                ResultCode.OK, true, null);
        tx.invalidate(ChangeTopic.ENGAGEMENT, eventId);
        ReminderRecord after = findByUserEvent(tx, userId, eventId);
        return after == null ? ReminderView.unconfigured()
                : view(after, tx.hasPendingEffect(EffectKey.REMINDER_RECONCILE));
    }

    void onEventTimeChanged(TxScope tx, long eventId, EventRecord after) {
        List<ReminderRecord> pending = remindersForEvent(tx, eventId, ReminderStatus.PENDING);
        for (ReminderRecord reminder : pending) {
            long triggerAt = ReminderRules.triggerAt(after.startAt, reminder.leadMinutes);
            ReminderStatus status = triggerAt <= tx.now()
                    ? ReminderStatus.EXPIRED : ReminderStatus.PENDING;
            updateStatus(tx, reminder, status, DeliveryState.PENDING,
                    ResultCode.OK, true, triggerAt);
        }
        if (!pending.isEmpty()) tx.invalidate(ChangeTopic.ENGAGEMENT, eventId);
    }

    void disablePendingForEvent(TxScope tx, long eventId) {
        List<ReminderRecord> pending = remindersForEvent(tx, eventId, ReminderStatus.PENDING);
        for (ReminderRecord reminder : pending) {
            updateStatus(tx, reminder, ReminderStatus.DISABLED, DeliveryState.PENDING,
                    ResultCode.OK, true, null);
        }
        if (!pending.isEmpty()) tx.invalidate(ChangeTopic.ENGAGEMENT, eventId);
    }

    List<ReminderRecord> allReminders(ReadScope scope) {
        List<ReminderRecord> result = new ArrayList<>();
        try (Cursor cursor = scope.db().query("reminders", null, null,
                null, null, null, "id ASC")) {
            while (cursor.moveToNext()) result.add(ReminderRecord.read(cursor));
        }
        return Collections.unmodifiableList(result);
    }

    ReminderRecord findById(ReadScope scope, long reminderId) {
        try (Cursor cursor = scope.db().query("reminders", null, "id=?",
                new String[]{Long.toString(reminderId)}, null, null, null)) {
            return cursor.moveToFirst() ? ReminderRecord.read(cursor) : null;
        }
    }

    void markDelivery(TxScope tx, ReminderRecord reminder,
                      DeliveryState deliveryState, ResultCode error) {
        ContentValues values = new ContentValues();
        values.put("deliveryState", deliveryState.name());
        values.put("deliveryError", error == ResultCode.OK ? "" : error.name());
        values.put("updatedAt", tx.now());
        tx.db().update("reminders", values, "id=? AND revision=?",
                new String[]{Long.toString(reminder.id), Long.toString(reminder.revision)});
        tx.invalidate(ChangeTopic.REMINDER_DELIVERY, reminder.eventId);
    }

    void markStatus(TxScope tx, ReminderRecord reminder, ReminderStatus status,
                    DeliveryState deliveryState, ResultCode error,
                    boolean incrementRevision, Long triggerAt) {
        updateStatus(tx, reminder, status, deliveryState, error, incrementRevision, triggerAt);
        tx.invalidate(ChangeTopic.REMINDER_DELIVERY, reminder.eventId);
        tx.invalidate(ChangeTopic.ENGAGEMENT, reminder.eventId);
    }

    private void updateStatus(TxScope tx, ReminderRecord reminder, ReminderStatus status,
                              DeliveryState deliveryState, ResultCode error,
                              boolean incrementRevision, Long triggerAt) {
        long revision = reminder.revision + (incrementRevision ? 1 : 0);
        String sql = "UPDATE reminders SET status=?,deliveryState=?,deliveryError=?,"
                + "revision=?,updatedAt=?"
                + (triggerAt == null ? "" : ",triggerAt=?")
                + " WHERE id=? AND revision=?";
        List<Object> args = new ArrayList<>();
        args.add(status.name());
        args.add(deliveryState.name());
        args.add(error == ResultCode.OK ? "" : error.name());
        args.add(revision);
        args.add(tx.now());
        if (triggerAt != null) args.add(triggerAt);
        args.add(reminder.id);
        args.add(reminder.revision);
        tx.db().execSQL(sql, args.toArray());
    }

    private int nextScheduleId(TxScope tx) {
        try (Cursor cursor = tx.db().rawQuery(
                "SELECT COALESCE(MAX(scheduleId),0)+1 FROM reminders", null)) {
            cursor.moveToFirst();
            long next = cursor.getLong(0);
            if (next <= 0 || next > Integer.MAX_VALUE) {
                throw new BusinessException(ResultCode.REMINDER_SCHEDULE_FAILED,
                        "系统提醒编号已耗尽");
            }
            return (int) next;
        }
    }

    private Set<Long> favoriteEvents(ReadScope scope, long userId) {
        Set<Long> result = new HashSet<>();
        try (Cursor cursor = scope.db().query("favorites", new String[]{"eventId"},
                "userId=?", new String[]{Long.toString(userId)}, null, null, null)) {
            while (cursor.moveToNext()) result.add(cursor.getLong(0));
        }
        return result;
    }

    private Map<Long, ReminderRecord> remindersByEvent(ReadScope scope, long userId) {
        Map<Long, ReminderRecord> result = new HashMap<>();
        try (Cursor cursor = scope.db().query("reminders", null, "userId=?",
                new String[]{Long.toString(userId)}, null, null, null)) {
            while (cursor.moveToNext()) {
                ReminderRecord record = ReminderRecord.read(cursor);
                result.put(record.eventId, record);
            }
        }
        return result;
    }

    private ReminderRecord findByUserEvent(ReadScope scope, long userId, long eventId) {
        try (Cursor cursor = scope.db().query("reminders", null,
                "userId=? AND eventId=?",
                new String[]{Long.toString(userId), Long.toString(eventId)},
                null, null, null)) {
            return cursor.moveToFirst() ? ReminderRecord.read(cursor) : null;
        }
    }

    private List<ReminderRecord> remindersForEvent(ReadScope scope, long eventId,
                                                   ReminderStatus status) {
        List<ReminderRecord> result = new ArrayList<>();
        try (Cursor cursor = scope.db().query("reminders", null,
                "eventId=? AND status=?",
                new String[]{Long.toString(eventId), status.name()},
                null, null, "id ASC")) {
            while (cursor.moveToNext()) result.add(ReminderRecord.read(cursor));
        }
        return result;
    }

    private ReminderView view(ReminderRecord record, boolean pendingEffect) {
        DeliveryState delivery = record.deliveryState;
        if (pendingEffect && delivery == DeliveryState.SCHEDULED) {
            delivery = DeliveryState.PENDING;
        }
        return new ReminderView(true, record.id, record.leadMinutes, record.triggerAt,
                record.status, delivery, parseError(record.deliveryError));
    }

    private ResultCode parseError(String value) {
        if (value == null || value.isEmpty()) return ResultCode.OK;
        try {
            return ResultCode.valueOf(value);
        } catch (IllegalArgumentException failure) {
            return ResultCode.INTERNAL_ERROR;
        }
    }
}
