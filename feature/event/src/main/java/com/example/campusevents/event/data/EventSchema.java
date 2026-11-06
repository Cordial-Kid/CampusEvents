package com.example.campusevents.event.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusevents.platform.db.SchemaContributor;

/** Owns only the events table. Related records are owned by C and D. */
public final class EventSchema implements SchemaContributor {
    @Override public String id() { return "event"; }

    @Override public void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT NOT NULL CHECK(length(trim(title)) > 0),"
                + "description TEXT NOT NULL,location TEXT NOT NULL CHECK(length(trim(location)) > 0),"
                + "startAt INTEGER NOT NULL,endAt INTEGER NOT NULL,registrationDeadline INTEGER NOT NULL,"
                + "capacity INTEGER NOT NULL CHECK(capacity > 0),category TEXT NOT NULL,"
                + "sourceType TEXT NOT NULL CHECK(sourceType IN ('OFFICIAL','MANUAL')),"
                + "sourceName TEXT NOT NULL,sourceUrl TEXT NOT NULL,"
                + "status TEXT NOT NULL CHECK(status IN ('ACTIVE','CANCELLED')),"
                + "createdBy INTEGER NOT NULL REFERENCES users(id),"
                + "createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,"
                + "version INTEGER NOT NULL CHECK(version > 0),"
                + "CHECK(registrationDeadline <= startAt AND startAt < endAt))");
        db.execSQL("CREATE INDEX event_start ON events(startAt,id)");
    }

    @Override public void seed(SQLiteDatabase db, long now) {
        String[] titles = {"课程演示 · Android 开发交流", "课程演示 · 校园运动体验", "课程演示 · 学术阅读分享"};
        String[] categories = {"TECH", "SPORT", "ACADEMIC"};
        for (int i = 0; i < titles.length; i++) {
            long start = now + (i + 2L) * 24 * 60 * 60 * 1000;
            ContentValues values = new ContentValues();
            values.put("title", titles[i]);
            values.put("description", "用于课程作业演示的本地样例活动，可由管理员编辑。时间按首次初始化时刻生成。");
            values.put("location", "教学楼 " + (i + 1) + "01");
            values.put("startAt", start);
            values.put("endAt", start + 2 * 60 * 60 * 1000);
            values.put("registrationDeadline", start - 60 * 60 * 1000);
            values.put("capacity", 20 + i * 10);
            values.put("category", categories[i]);
            values.put("sourceType", "MANUAL");
            values.put("sourceName", "课程作业演示数据");
            values.put("sourceUrl", "");
            values.put("status", "ACTIVE");
            values.put("createdBy", 1L);
            values.put("createdAt", now);
            values.put("updatedAt", now);
            values.put("version", 1L);
            db.insertOrThrow("events", null, values);
        }
    }
}
