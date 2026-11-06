package com.example.campusevents.platform.data;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.invalidation.ChangeSet;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import java.util.LinkedHashSet;
import java.util.Set;

public final class TxScope extends ReadScope {
    private final Set<ChangeSet> changes = new LinkedHashSet<>();

    TxScope(SQLiteDatabase database, SessionSnapshot session, long now) {
        super(database, session, now);
    }

    public void invalidate(ChangeTopic topic, long eventId) { invalidate(new ChangeSet(topic, eventId)); }
    public void invalidate(ChangeSet change) { verifyActive(); changes.add(change); }
    Set<ChangeSet> changes() { return changes; }

    public void requestEffect(EffectKey key) {
        SQLiteDatabase database = db();
        ContentValues initial = new ContentValues();
        initial.put("effectKey", key.name());
        initial.put("updatedAt", now());
        database.insertWithOnConflict("system_effects", null, initial, SQLiteDatabase.CONFLICT_IGNORE);
        database.execSQL("UPDATE system_effects SET requestedVersion=requestedVersion+1, "
                + "attempts=0,lastError='',updatedAt=? WHERE effectKey=?", new Object[]{now(),key.name()});
    }
}
