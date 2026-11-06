package com.example.campusevents.registration;

import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.platform.data.*;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.port.*;
import android.database.sqlite.SQLiteDatabase;
import java.util.List;

/** C replaces this development adapter while keeping the module export stable. */
public final class RegistrationModule {
    private final DataAccess data;
    public RegistrationModule(DataAccess data, EventReadPort events) { this.data = data; }
    public boolean isReady() { return false; }
    private static BusinessException unavailable() {
        return new BusinessException(ResultCode.MODULE_NOT_READY, "报名模块待 C 接入");
    }
    private <T> AsyncCall<T> missing() { return data.read(scope -> { throw unavailable(); }); }
    public RegistrationApi api() { return new RegistrationApi() {
        public AsyncCall<RegistrationSummary> getSummary(long eventId) { return missing(); }
        public AsyncCall<RegistrationSummary> register(long eventId) { return missing(); }
        public AsyncCall<RegistrationSummary> cancelRegistration(long eventId) { return missing(); }
        public AsyncCall<List<RegistrationItem>> listMine() { return missing(); }
    }; }
    public RegistrationReadPort readPort() { return (scope, events) -> ModuleValue.notReady(); }
    public RegistrationEventPort eventPort() { return new RegistrationEventPort() {
        public int countActive(TxScope tx, long eventId) { throw unavailable(); }
        public void cancelActiveForEvent(TxScope tx, long eventId) { throw unavailable(); }
    }; }
    public static SchemaContributor schema() { return new SchemaContributor() {
        public String id() { return "registration"; }
        public void create(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE registrations (id INTEGER PRIMARY KEY AUTOINCREMENT,userId INTEGER NOT NULL REFERENCES users(id),eventId INTEGER NOT NULL REFERENCES events(id),status TEXT NOT NULL CHECK(status IN ('REGISTERED','CANCELLED')),cancelReason TEXT NOT NULL DEFAULT 'NONE' CHECK(cancelReason IN ('NONE','USER_CANCELLED','EVENT_CANCELLED')),registeredAt INTEGER NOT NULL,cancelledAt INTEGER,UNIQUE(userId,eventId),CHECK((status='REGISTERED' AND cancelReason='NONE' AND cancelledAt IS NULL) OR (status='CANCELLED' AND cancelReason<>'NONE' AND cancelledAt IS NOT NULL)))");
            db.execSQL("CREATE INDEX reg_event_status ON registrations(eventId,status)");
        }
    }; }
}
