package com.example.campusevents.platform.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.contract.session.*;
import com.example.campusevents.platform.port.EventReadPort;
import com.example.campusevents.registration.RegistrationModule;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Real SQLite and production C module; deterministic clock for exact boundaries.
 * Synchronous DataAccess is a test-only driver, not a substitute for platform queue tests.
 */
@RunWith(AndroidJUnit4.class)
public class RegistrationRegressionTest {
    private SQLiteDatabase db;
    private Driver driver;
    private RegistrationModule module;
    private EventRecord event;
    private static final long START=10000, END=20000, DEADLINE=9000;
    @Before public void setup() {
        db=SQLiteDatabase.create(null);db.setForeignKeyConstraintsEnabled(true);
        db.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY)");
        db.execSQL("INSERT INTO users VALUES(1),(2),(3)");
        db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY)");db.execSQL("INSERT INTO events VALUES(1)");
        RegistrationModule.schema().create(db);driver=new Driver();event=event(1,EventStatus.ACTIVE);
        module=new RegistrationModule(driver,new EventReadPort() {
            public EventRecord require(ReadScope s,long id) {
                if(id!=1) throw new BusinessException(ResultCode.EVENT_NOT_FOUND,"missing");return event;
            }
            public List<EventRecord> list(ReadScope s,EventFilter f) {return Collections.singletonList(event);}
        });
    }
    @After public void close() {db.close();}
    private EventRecord event(int capacity,EventStatus status) {
        return new EventRecord(1,"测试活动","说明","教室",START,END,DEADLINE,capacity,"TECH",SourceType.MANUAL,"测试","",status,1,0,0,1);
    }
    private <T> ApiResult<T> run(AsyncCall<T> call) {AtomicReference<ApiResult<T>> result=new AtomicReference<>();call.enqueue(result::set);return result.get();}
    private int scalar(String sql) {try(Cursor c=db.rawQuery(sql,null)) {c.moveToFirst();return c.getInt(0);}}
    @Test public void duplicateAndLastSeatNeverIncreaseCount() {
        assertTrue(run(module.api().register(1)).success);
        assertEquals(ResultCode.ALREADY_REGISTERED,run(module.api().register(1)).code);
        driver.user=3;assertEquals(ResultCode.EVENT_FULL,run(module.api().register(1)).code);
        assertEquals(1,scalar("SELECT count(*) FROM registrations WHERE status='REGISTERED'"));
        assertEquals(0,run(module.api().getSummary(1)).data.remainingCapacity);
    }
    @Test public void cancelIsIdempotentAndReRegistrationReusesRow() {
        run(module.api().register(1));long id=run(module.api().listMine()).data.get(0).id;
        assertTrue(run(module.api().cancelRegistration(1)).success);
        assertTrue(run(module.api().cancelRegistration(1)).success);
        assertEquals(1,run(module.api().getSummary(1)).data.remainingCapacity);
        driver.now=2000;assertTrue(run(module.api().register(1)).success);
        RegistrationItem item=run(module.api().listMine()).data.get(0);
        assertEquals(id,item.id);assertEquals(2000,item.registeredAt);assertNull(item.cancelledAt);
        assertEquals(1,scalar("SELECT count(*) FROM registrations"));
    }
    @Test public void exactDeadlineClosesRegistrationButAllowsCancellation() {
        run(module.api().register(1));driver.now=DEADLINE;
        assertTrue(run(module.api().getSummary(1)).data.canCancel);
        assertTrue(run(module.api().cancelRegistration(1)).success);
        assertEquals(ResultCode.REGISTRATION_CLOSED,run(module.api().register(1)).code);
        driver.now=START;assertEquals(ResultCode.EVENT_STARTED,run(module.api().register(1)).code);
    }
    @Test public void startPreventsCancellationAndMissingCancelCreatesNoRows() {
        assertTrue(run(module.api().cancelRegistration(1)).success);assertEquals(0,scalar("SELECT count(*) FROM registrations"));
        run(module.api().register(1));driver.now=START;
        assertEquals(ResultCode.EVENT_STARTED,run(module.api().cancelRegistration(1)).code);
        assertEquals(1,run(module.api().getSummary(1)).data.activeCount);
    }
    @Test public void identityAndHistoryAreIsolated() {
        driver.user=0;assertEquals(ResultCode.NOT_LOGGED_IN,run(module.api().register(1)).code);
        driver.user=1;assertEquals(ResultCode.FORBIDDEN,run(module.api().listMine()).code);
        assertEquals(ResultCode.FORBIDDEN,run(module.attendanceApi().checkIn(1)).code);
        driver.user=2;run(module.api().register(1));driver.user=3;
        assertTrue(run(module.api().listMine()).data.isEmpty());
        assertEquals(RegistrationStatus.NONE,run(module.api().getSummary(1)).data.myStatus);
        assertEquals(ResultCode.EVENT_NOT_FOUND,run(module.api().getSummary(999)).code);
    }
    @Test public void eventCancellationAndFailureRollbackAreAtomic() {
        event=event(2,EventStatus.ACTIVE);run(module.api().register(1));driver.user=3;run(module.api().register(1));
        ApiResult<Void> failure=run(driver.write(tx -> {
            module.eventPort().cancelActiveForEvent(tx,1);
            throw new BusinessException(ResultCode.MODULE_NOT_READY,"D failed");
        }));
        assertEquals(ResultCode.MODULE_NOT_READY,failure.code);
        assertEquals(2,scalar("SELECT count(*) FROM registrations WHERE status='REGISTERED'"));
        run(driver.write(tx -> {module.eventPort().cancelActiveForEvent(tx,1);return null;}));
        assertEquals(0,run(driver.read(s -> module.readPort().summaries(s,Collections.singletonList(event)).data.get(1L).activeCount)).data.intValue());
        assertEquals(2,scalar("SELECT count(*) FROM registrations WHERE cancelReason='EVENT_CANCELLED' AND cancelledAt IS NOT NULL"));
        event=event(2,EventStatus.CANCELLED);assertEquals(ResultCode.EVENT_CANCELLED,run(module.api().register(1)).code);
    }
    @Test public void attendanceWindowAndFeedbackValidation() {
        assertFalse(run(module.attendanceApi().checkIn(1)).success);
        run(module.api().register(1));assertFalse(run(module.attendanceApi().checkIn(1)).success);
        driver.now=START;assertTrue(run(module.attendanceApi().checkIn(1)).success);
        driver.now=START+1;assertEquals(Long.valueOf(START),run(module.attendanceApi().checkIn(1)).data.checkedInAt);
        assertFalse(run(module.attendanceApi().submitFeedback(1,0,"反馈")).success);
        assertFalse(run(module.attendanceApi().submitFeedback(1,5," ")).success);
        assertFalse(run(module.attendanceApi().submitFeedback(1,5,String.join("",Collections.nCopies(501,"字")))).success);
        driver.now=END;assertTrue(run(module.attendanceApi().submitFeedback(1,5,"很有收获")).success);
        assertEquals("改进建议",run(module.attendanceApi().submitFeedback(1,4,"改进建议")).data.feedback);
        assertEquals(1,scalar("SELECT count(*) FROM reg_attendance"));
        driver.user=3;assertNull(run(module.attendanceApi().get(1)).data.checkedInAt);
        assertFalse(run(module.attendanceApi().submitFeedback(1,5,"越权")).success);
    }
    @Test public void exactEndClosesCheckInAndCancelledActivityBlocksFeedback() {
        run(module.api().register(1));driver.now=END;assertFalse(run(module.attendanceApi().checkIn(1)).success);
        driver.now=START;run(module.attendanceApi().checkIn(1));event=event(1,EventStatus.CANCELLED);
        assertFalse(run(module.attendanceApi().submitFeedback(1,5,"无效")).success);
    }
    @Test public void migrationKeepsExistingRegistration() {
        run(module.api().register(1));db.execSQL("DROP TABLE reg_attendance");
        RegistrationModule.schema().migrate(db,1,2);
        assertEquals(1,scalar("SELECT count(*) FROM registrations"));
        assertEquals(0,scalar("SELECT count(*) FROM reg_attendance"));
        assertEquals(0,scalar("SELECT count(*) FROM pragma_foreign_key_check"));
    }
    @Test public void databaseConstraintsRejectDuplicateAndInvalidAttendance() {
        run(module.api().register(1));
        try {db.execSQL("INSERT INTO registrations(userId,eventId,status,cancelReason,registeredAt) VALUES(2,1,'REGISTERED','NONE',0)");fail();}
        catch(android.database.sqlite.SQLiteConstraintException expected) {}
        assertEquals(1,scalar("SELECT count(*) FROM registrations"));
    }
    private final class Driver implements DataAccess {
        long now=1000,user=2;
        private SessionSnapshot session() {return new SessionSnapshot(user==0?null:new UserSnapshot(user,"test",user==1?Role.ADMIN:Role.STUDENT),user);}
        public <T> AsyncCall<T> read(ReadWork<T> work) {return call(() -> {
            ReadScope s=new ReadScope(db,session(),now);try {return work.run(s);} finally {s.finish();}
        });}
        public <T> AsyncCall<T> write(WriteWork<T> work) {return call(() -> {
            db.beginTransaction();TxScope s=new TxScope(db,session(),now);
            try {T value=work.run(s);db.setTransactionSuccessful();return value;} finally {s.finish();db.endTransaction();}
        });}
        private <T> AsyncCall<T> call(java.util.concurrent.Callable<T> work) {return callback -> {
            ApiResult<T> result;
            try {result=ApiResult.ok(work.call());} catch(BusinessException e) {result=ApiResult.failure(e.code,e.getMessage());}
            catch(Exception e) {throw new AssertionError(e);}
            callback.onResult(result);return () -> {};
        };}
    }
}
