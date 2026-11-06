package com.example.campusevents.platform.data;

import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.contract.session.UserSnapshot;
import com.example.campusevents.platform.effect.EffectKey;

/** Valid only inside the supplied work, on the serial database thread. */
public class ReadScope {
    private final SQLiteDatabase database;
    private final SessionSnapshot session;
    private final long now;
    private final Thread thread = Thread.currentThread();
    private boolean active = true;

    ReadScope(SQLiteDatabase database, SessionSnapshot session, long now) {
        this.database = database;
        this.session = session;
        this.now = now;
    }

    final void verifyActive() {
        if (!active || Thread.currentThread() != thread) {
            throw new IllegalStateException("Database scope escaped its work");
        }
    }

    final void finish() { active = false; }
    public SQLiteDatabase db() { verifyActive(); return database; }
    public SessionSnapshot session() { verifyActive(); return session; }
    public UserSnapshot actor() { return session().user; }
    public long sessionEpoch() { return session().epoch; }
    public long now() { verifyActive(); return now; }
    public boolean hasPendingEffect(EffectKey key) {
        try (Cursor cursor = db().rawQuery("SELECT 1 FROM system_effects WHERE effectKey=? "
                + "AND requestedVersion>appliedVersion", new String[]{key.name()})) {
            return cursor.moveToFirst();
        }
    }
}
