package com.example.campusevents.event.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventInput;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.platform.data.BusinessException;
import com.example.campusevents.platform.data.ReadScope;
import com.example.campusevents.platform.port.EventReadPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EventStore implements EventReadPort {
    @Override public EventRecord require(ReadScope scope, long eventId) {
        try (Cursor cursor = scope.db().query("events", null, "id=?",
                new String[]{Long.toString(eventId)}, null, null, null)) {
            if (!cursor.moveToFirst()) {
                throw new BusinessException(ResultCode.EVENT_NOT_FOUND, "活动不存在，请刷新列表");
            }
            return read(cursor);
        }
    }

    @Override public List<EventRecord> list(ReadScope scope, EventFilter requested) {
        EventFilter filter = requested == null ? new EventFilter() : requested;
        StringBuilder selection = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (filter.startAtFrom != null) {
            selection.append(" AND startAt>=?");
            args.add(Long.toString(filter.startAtFrom));
        }
        if (filter.startAtUntil != null) {
            selection.append(" AND startAt<?");
            args.add(Long.toString(filter.startAtUntil));
        }
        if (filter.category != null && !filter.category.trim().isEmpty()) {
            selection.append(" AND category=?");
            args.add(filter.category.trim());
        }
        if (filter.keyword != null && !filter.keyword.trim().isEmpty()) {
            selection.append(" AND (instr(lower(title),lower(?))>0 OR instr(lower(location),lower(?))>0)");
            args.add(filter.keyword.trim());
            args.add(filter.keyword.trim());
        }
        if (!filter.includeCancelled) selection.append(" AND status='ACTIVE'");
        List<EventRecord> result = new ArrayList<>();
        try (Cursor cursor = scope.db().query("events", null, selection.toString(),
                args.toArray(new String[0]), null, null, "startAt ASC,id ASC")) {
            while (cursor.moveToNext()) result.add(read(cursor));
        }
        return Collections.unmodifiableList(result);
    }

    public long insert(SQLiteDatabase db, EventInput input, long userId, long now) {
        ContentValues values = editableValues(input);
        values.put("status", EventStatus.ACTIVE.name());
        values.put("createdBy", userId);
        values.put("createdAt", now);
        values.put("updatedAt", now);
        values.put("version", 1L);
        return db.insertOrThrow("events", null, values);
    }

    public void update(SQLiteDatabase db, EventRecord before, EventInput input, long now) {
        ContentValues values = editableValues(input);
        values.put("updatedAt", now);
        values.put("version", before.version + 1);
        updateVersioned(db, before, values);
    }

    public void cancel(SQLiteDatabase db, EventRecord before, long now) {
        ContentValues values = new ContentValues();
        values.put("status", EventStatus.CANCELLED.name());
        values.put("updatedAt", now);
        values.put("version", before.version + 1);
        updateVersioned(db, before, values);
    }

    private void updateVersioned(SQLiteDatabase db, EventRecord before, ContentValues values) {
        int count = db.update("events", values, "id=? AND version=?",
                new String[]{Long.toString(before.id), Long.toString(before.version)});
        if (count != 1) throw new BusinessException(ResultCode.STALE_DATA, "活动已被修改，请刷新后重试");
    }

    private ContentValues editableValues(EventInput input) {
        ContentValues values = new ContentValues();
        values.put("title", input.title.trim());
        values.put("description", input.description.trim());
        values.put("location", input.location.trim());
        values.put("startAt", input.startAt);
        values.put("endAt", input.endAt);
        values.put("registrationDeadline", input.registrationDeadline);
        values.put("capacity", input.capacity);
        values.put("category", input.category.trim());
        values.put("sourceType", input.sourceType.name());
        values.put("sourceName", input.sourceName.trim());
        values.put("sourceUrl", input.sourceUrl.trim());
        return values;
    }

    private EventRecord read(Cursor c) {
        return new EventRecord(number(c, "id"), text(c, "title"), text(c, "description"),
                text(c, "location"), number(c, "startAt"), number(c, "endAt"),
                number(c, "registrationDeadline"), (int) number(c, "capacity"), text(c, "category"),
                SourceType.valueOf(text(c, "sourceType")), text(c, "sourceName"), text(c, "sourceUrl"),
                EventStatus.valueOf(text(c, "status")), number(c, "createdBy"),
                number(c, "createdAt"), number(c, "updatedAt"), number(c, "version"));
    }

    private String text(Cursor cursor, String column) { return cursor.getString(cursor.getColumnIndexOrThrow(column)); }
    private long number(Cursor cursor, String column) { return cursor.getLong(cursor.getColumnIndexOrThrow(column)); }
}
