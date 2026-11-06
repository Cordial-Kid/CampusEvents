package com.example.campusevents.platform.db;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CampusDatabase extends SQLiteOpenHelper {
    public static final String NAME = "campus_events.db";
    public static final int VERSION = 1;
    private final List<SchemaContributor> contributors;

    public CampusDatabase(Context context, List<SchemaContributor> contributors) {
        super(context.getApplicationContext(), NAME, null, VERSION);
        this.contributors = new ArrayList<>(contributors);
        Set<String> ids = new HashSet<>();
        for (SchemaContributor contributor : this.contributors) {
            if (!ids.add(contributor.id())) {
                throw new IllegalArgumentException("Duplicate schema " + contributor.id());
            }
        }
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY CHECK(id > 0), "
                + "displayName TEXT NOT NULL, role TEXT NOT NULL CHECK(role IN ('ADMIN','STUDENT')))");
        db.execSQL("CREATE TABLE app_session (singletonId INTEGER PRIMARY KEY CHECK(singletonId=1), "
                + "currentUserId INTEGER REFERENCES users(id), epoch INTEGER NOT NULL CHECK(epoch>=0))");
        db.execSQL("CREATE TABLE system_effects (effectKey TEXT PRIMARY KEY, "
                + "requestedVersion INTEGER NOT NULL DEFAULT 0, appliedVersion INTEGER NOT NULL DEFAULT 0, "
                + "attempts INTEGER NOT NULL DEFAULT 0, lastError TEXT NOT NULL DEFAULT '', "
                + "updatedAt INTEGER NOT NULL, CHECK(requestedVersion>=appliedVersion))");
        db.execSQL("INSERT INTO users(id,displayName,role) VALUES "
                + "(1,'管理员','ADMIN'),(2,'学生甲','STUDENT'),(3,'学生乙','STUDENT')");
        db.execSQL("INSERT INTO app_session(singletonId,currentUserId,epoch) VALUES (1,NULL,0)");
        for (SchemaContributor contributor : contributors) contributor.create(db);
        long now = System.currentTimeMillis();
        for (SchemaContributor contributor : contributors) contributor.seed(db, now);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Raising VERSION requires an explicit, reviewed migration in every registered owner.
        for (SchemaContributor contributor : contributors) {
            contributor.migrate(db, oldVersion, newVersion);
        }
    }
}
