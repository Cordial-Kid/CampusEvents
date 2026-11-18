package com.example.campusevents.platform.data;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.platform.db.*;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.port.EventReadPort;
import com.example.campusevents.registration.RegistrationModule;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Runs production C through the real asynchronous serial queue and reopens SQLite. */
@RunWith(AndroidJUnit4.class)
public class RegistrationQueueTest {
    @Test public void rapidRequestsUseOneSeatAndPersistAcrossRestart() throws Exception {
        // Library instrumentation target is an isolated test APK, not the installed app.
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(CampusDatabase.NAME);
        long start=System.currentTimeMillis()+86400000;
        EventRecord event=new EventRecord(1,"队列活动","说明","教室",start,start+3600000,start,1,"TECH",SourceType.MANUAL,"测试","",EventStatus.ACTIVE,1,0,0,1);
        SchemaContributor fixture=new SchemaContributor() {
            public String id() {return "event_fixture";}
            public void create(SQLiteDatabase db) {db.execSQL("CREATE TABLE events(id INTEGER PRIMARY KEY)");db.execSQL("INSERT INTO events VALUES(1)");}
        };
        EventReadPort port=new EventReadPort() {
            public EventRecord require(ReadScope scope,long id) {return event;}
            public List<EventRecord> list(ReadScope scope,EventFilter filter) {return Collections.singletonList(event);}
        };
        List<SchemaContributor> schemas=Arrays.asList(fixture,RegistrationModule.schema());
        SerialDataAccess data=new SerialDataAccess(new CampusDatabase(context,schemas),new DataInvalidation());
        try {
            data.start();assertTrue(await(data.current()).success);await(data.switchDemoUser(2));
            RegistrationModule module=new RegistrationModule(data,port);
            CountDownLatch delivered=new CountDownLatch(8);AtomicInteger accepted=new AtomicInteger(),duplicate=new AtomicInteger();
            for(int i=0;i<8;i++) module.api().register(1).enqueue(result -> {
                if(result.success) accepted.incrementAndGet();
                if(result.code==ResultCode.ALREADY_REGISTERED) duplicate.incrementAndGet();
                delivered.countDown();
            });
            assertTrue(delivered.await(10,TimeUnit.SECONDS));assertEquals(1,accepted.get());assertEquals(7,duplicate.get());
            await(data.switchDemoUser(3));assertEquals(ResultCode.EVENT_FULL,await(module.api().register(1)).code);
            data.close();assertTrue(data.awaitClosed(10,TimeUnit.SECONDS));
            data=new SerialDataAccess(new CampusDatabase(context,schemas),new DataInvalidation());data.start();await(data.current());
            module=new RegistrationModule(data,port);
            assertEquals(1,await(module.api().getSummary(1)).data.activeCount);
            await(data.switchDemoUser(2));assertEquals(1,await(module.api().listMine()).data.size());
        } finally {
            data.close();assertTrue(data.awaitClosed(10,TimeUnit.SECONDS));context.deleteDatabase(CampusDatabase.NAME);
        }
    }
    private static <T> ApiResult<T> await(AsyncCall<T> call) throws Exception {
        AtomicReference<ApiResult<T>> result=new AtomicReference<>();CountDownLatch latch=new CountDownLatch(1);
        call.enqueue(value -> {result.set(value);latch.countDown();});
        assertTrue(latch.await(10,TimeUnit.SECONDS));return result.get();
    }
}
