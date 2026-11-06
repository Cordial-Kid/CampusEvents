package com.example.campusevents.platform.db;

import android.database.sqlite.SQLiteDatabase;

/** Ordered by the application before the one shared database is opened. */
public interface SchemaContributor {
    String id();
    void create(SQLiteDatabase db);
    default void seed(SQLiteDatabase db, long now) {}
    default void migrate(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("Missing migration for " + id()
                + " from " + oldVersion + " to " + newVersion);
    }
}
