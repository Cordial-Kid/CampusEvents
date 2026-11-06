package com.example.campusevents.engagement;

import android.database.sqlite.SQLiteDatabase;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.engagement.*;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.platform.data.*;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.*;
import com.example.campusevents.platform.port.*;
import com.example.campusevents.platform.ui.NotificationRouteFactory;
import java.util.List;

/** D owns these tables and replaces the explicit development adapters. */
public final class EngagementModule {
    private final DataAccess data;
    public EngagementModule(DataAccess data, EventReadPort events, NotificationRouteFactory routes) { this.data = data; }
    public boolean isReady() { return false; }
    private static BusinessException unavailable() {
        return new BusinessException(ResultCode.MODULE_NOT_READY, "收藏提醒模块待 D 接入");
    }
    private <T> AsyncCall<T> missing() { return data.read(scope -> { throw unavailable(); }); }
    public FavoriteApi favoriteApi() { return new FavoriteApi() {
        public AsyncCall<Boolean> setFavorite(long eventId, boolean desiredState) { return missing(); }
        public AsyncCall<List<FavoriteItem>> listMine() { return missing(); }
    }; }
    public ReminderApi reminderApi() { return new ReminderApi() {
        public AsyncCall<ReminderView> get(long eventId) { return missing(); }
        public AsyncCall<ReminderView> set(long eventId, int leadMinutes) { return missing(); }
        public AsyncCall<ReminderView> disable(long eventId) { return missing(); }
    }; }
    public EngagementReadPort readPort() { return (scope, events) -> ModuleValue.notReady(); }
    public ReminderEventPort eventPort() { return new ReminderEventPort() {
        public void onEventTimeChanged(TxScope tx, EventRecord before, EventRecord after) { throw unavailable(); }
        public void disablePendingForEvent(TxScope tx, long eventId) { throw unavailable(); }
    }; }
    public EffectHandler effectHandler() { return new EffectHandler() {
        public EffectKey key() { return EffectKey.REMINDER_RECONCILE; }
        public EffectOutcome reconcile(EffectContext context) { return EffectOutcome.MODULE_NOT_READY; }
    }; }
    public static SchemaContributor schema() { return new SchemaContributor() {
        public String id() { return "engagement"; }
        public void create(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE favorites (id INTEGER PRIMARY KEY AUTOINCREMENT,userId INTEGER NOT NULL REFERENCES users(id),eventId INTEGER NOT NULL REFERENCES events(id),createdAt INTEGER NOT NULL,UNIQUE(userId,eventId))");
            db.execSQL("CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT,userId INTEGER NOT NULL REFERENCES users(id),eventId INTEGER NOT NULL REFERENCES events(id),leadMinutes INTEGER NOT NULL CHECK(leadMinutes>0),triggerAt INTEGER NOT NULL,status TEXT NOT NULL CHECK(status IN ('PENDING','DISABLED','SENT','EXPIRED')),revision INTEGER NOT NULL DEFAULT 1 CHECK(revision>0),deliveryState TEXT NOT NULL DEFAULT 'PENDING' CHECK(deliveryState IN ('PENDING','SCHEDULED','BLOCKED','FAILED','NOT_REQUIRED')),deliveryError TEXT NOT NULL DEFAULT '',scheduleId INTEGER NOT NULL UNIQUE,createdAt INTEGER NOT NULL,updatedAt INTEGER NOT NULL,UNIQUE(userId,eventId))");
            db.execSQL("CREATE INDEX eng_reminder_status ON reminders(status,triggerAt)");
        }
    }; }
}
