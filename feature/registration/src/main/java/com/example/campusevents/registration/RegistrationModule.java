package com.example.campusevents.registration;
import android.database.sqlite.SQLiteDatabase;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.platform.data.*;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.port.*;
import java.util.*;

public final class RegistrationModule {
    private final DataAccess data;
    private final EventReadPort events;
    private final RegistrationStore store = new RegistrationStore();
    public RegistrationModule(DataAccess data, EventReadPort events) { this.data=data; this.events=events; }
    public boolean isReady() { return true; }
    public RegistrationApi api() { return new RegistrationApi() {
        public AsyncCall<RegistrationSummary> getSummary(long id) {
            return data.read(s -> store.summary(s, events.require(s,id)));
        }
        public AsyncCall<RegistrationSummary> register(long id) {
            return data.write(s -> store.register(s,events.require(s,id)));
        }
        public AsyncCall<RegistrationSummary> cancelRegistration(long id) {
            return data.write(s -> store.cancel(s,events.require(s,id)));
        }
        public AsyncCall<List<RegistrationItem>> listMine() { return data.read(s -> store.mine(s,events)); }
    }; }
    public AttendanceApi attendanceApi() { return new AttendanceApi() {
        public AsyncCall<AttendanceView> get(long id) { return data.read(s -> store.attendance(s,events.require(s,id))); }
        public AsyncCall<AttendanceView> checkIn(long id) { return data.write(s -> store.checkIn(s,events.require(s,id))); }
        public AsyncCall<AttendanceView> submitFeedback(long id, int rating, String text) {
            return data.write(s -> store.feedback(s,events.require(s,id),rating,text));
        }
    }; }
    public RegistrationReadPort readPort() { return (scope, records) -> {
        Map<Long,RegistrationSummary> result=new LinkedHashMap<>();
        for (com.example.campusevents.contract.event.EventRecord event:records)
            result.put(event.id,store.summary(scope,event));
        return ModuleValue.ready(Collections.unmodifiableMap(result));
    }; }
    public RegistrationEventPort eventPort() { return new RegistrationEventPort() {
        public int countActive(TxScope tx,long id) { return store.count(tx,id); }
        public void cancelActiveForEvent(TxScope tx,long id) { store.cancelEvent(tx,id); }
    }; }
    private static void createAttendance(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE reg_attendance (registrationId INTEGER PRIMARY KEY REFERENCES registrations(id),checkedInAt INTEGER NOT NULL,rating INTEGER CHECK(rating BETWEEN 1 AND 5),feedback TEXT NOT NULL DEFAULT '' CHECK(length(feedback)<=500),submittedAt INTEGER,CHECK((rating IS NULL AND submittedAt IS NULL AND feedback='') OR (rating IS NOT NULL AND submittedAt IS NOT NULL)))");
    }
    public static SchemaContributor schema() { return new SchemaContributor() {
        public void migrate(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) createAttendance(db);
            else throw new IllegalStateException("Unsupported registration migration");
        }
        public String id() { return "registration"; }
        public void create(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE registrations (id INTEGER PRIMARY KEY AUTOINCREMENT,userId INTEGER NOT NULL REFERENCES users(id),eventId INTEGER NOT NULL REFERENCES events(id),status TEXT NOT NULL CHECK(status IN ('REGISTERED','CANCELLED')),cancelReason TEXT NOT NULL DEFAULT 'NONE' CHECK(cancelReason IN ('NONE','USER_CANCELLED','EVENT_CANCELLED')),registeredAt INTEGER NOT NULL,cancelledAt INTEGER,UNIQUE(userId,eventId),CHECK((status='REGISTERED' AND cancelReason='NONE' AND cancelledAt IS NULL) OR (status='CANCELLED' AND cancelReason<>'NONE' AND cancelledAt IS NOT NULL)))");
            db.execSQL("CREATE INDEX reg_event_status ON registrations(eventId,status)");
            createAttendance(db);
        }
    }; }
}
