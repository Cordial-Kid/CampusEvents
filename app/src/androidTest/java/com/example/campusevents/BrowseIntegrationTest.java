package com.example.campusevents;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.example.campusevents.bootstrap.AppGraph;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.contract.registration.*;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.ui.*;
import com.example.campusevents.registration.RegistrationModule;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Predicate;
import org.junit.*;
import org.junit.runner.RunWith;

/** Full production A/B/C/D graph. Only uniquely created test rows are removed. */
@RunWith(AndroidJUnit4.class)
public final class BrowseIntegrationTest {
    private AppGraph graph;
    private ActivityScenario<MainActivity> scenario;
    private RegistrationApi registrations;
    private Context context;
    private Long originalUser;
    private final List<Long> created = new ArrayList<>();
    private SharedPreferences preferences;
    private final Map<String, Set<String>> originalInterests = new HashMap<>();

    @Before public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        graph = ((CampusApplication) context.getApplicationContext()).graph();
        com.example.campusevents.contract.session.UserSnapshot actor =
                await(graph.platform.sessionApi().current()).user;
        originalUser = actor == null ? null : actor.userId;
        preferences = context.getSharedPreferences("browse_interests", Context.MODE_PRIVATE);
        for (String key : Arrays.asList("2", "3")) {
            originalInterests.put(key, preferences.contains(key)
                    ? new HashSet<>(preferences.getStringSet(key, Collections.emptySet())) : null);
            assertTrue(preferences.edit().putStringSet(key, Collections.singleton("TECH")).commit());
        }
        registrations = new RegistrationModule(graph.platform.dataAccess(), graph.events.readPort()).api();
        scenario = ActivityScenario.launch(MainActivity.class);
        switchUser(1);
    }

    @After public void tearDown() throws Exception {
        if (scenario != null) scenario.close();
        if (graph == null) return;
        await(graph.platform.dataAccess().write(tx -> {
            for (long id : created) {
                String[] args = {Long.toString(id)};
                tx.db().execSQL("DELETE FROM reg_attendance WHERE registrationId IN "
                        + "(SELECT id FROM registrations WHERE eventId=?)", args);
                tx.db().delete("registrations", "eventId=?", args);
                tx.db().delete("favorites", "eventId=?", args);
                tx.db().delete("reminders", "eventId=?", args);
                tx.db().delete("events", "id=?", args);
            }
            tx.invalidate(ChangeTopic.EVENT_CONTENT, 0);
            return null;
        }));
        SharedPreferences.Editor editor = preferences.edit();
        for (Map.Entry<String, Set<String>> entry : originalInterests.entrySet()) {
            if (entry.getValue() == null) editor.remove(entry.getKey());
            else editor.putStringSet(entry.getKey(), entry.getValue());
        }
        assertTrue(editor.commit());
        if (originalUser != null) await(graph.platform.sessionApi().switchDemoUser(originalUser));
        else {
            awaitResult(graph.platform.dataAccess().write(tx -> {
                tx.db().execSQL("UPDATE app_session SET currentUserId=NULL,epoch=epoch+1 WHERE singletonId=1");
                tx.invalidate(ChangeTopic.SESSION, 0);
                return null;
            }));
        }
    }

    @Test public void registrationRefreshIsolationAndRecommendationsUseRealPorts() throws Exception {
        EventRecord event = create("registration", 1, false);
        switchUser(2);
        list();
        waitFor("initial unregistered card", a -> card(a, event.title, "剩余 1 名", "我未报名"));
        waitFor("eligible recommendation", a -> recommended(a, event.title));

        await(registrations.register(event.id));
        waitFor("registration refresh", a -> card(a, event.title, "剩余 0 名", "我已报名"));
        waitFor("registered activity removed from recommendations", a -> !recommended(a, event.title));
        scenario.recreate();
        waitFor("registered status survives recreation", a -> card(a, event.title, "我已报名"));

        switchUser(3);
        list();
        waitFor("second student isolated and event full", a ->
                card(a, event.title, "剩余 0 名", "我未报名") && !recommended(a, event.title));
        ApiResult<RegistrationSummary> full = awaitResult(registrations.register(event.id));
        assertEquals(ResultCode.EVENT_FULL, full.code);

        switchUser(2);
        list();
        await(registrations.cancelRegistration(event.id));
        waitFor("cancel restores capacity and recommendation", a ->
                card(a, event.title, "剩余 1 名", "我的报名已取消") && recommended(a, event.title));
        scenario.moveToState(Lifecycle.State.CREATED);
        scenario.moveToState(Lifecycle.State.RESUMED);
        waitFor("return refresh", a -> card(a, event.title, "剩余 1 名"));
    }

    @Test public void editsDeadlineCancellationAndDetailRecoveryRefreshBrowse() throws Exception {
        EventRecord event = create("edited", 3, false);
        EventRecord closed = create("closed", 3, true);
        switchUser(2);
        list();
        waitFor("deadline rejects recommendation", a -> card(a, closed.title, "报名已截止")
                && !recommended(a, closed.title));

        switchUser(1);
        EventInput changed = new EventInput(event.title + "-updated", "Updated description",
                "Updated room", event.startAt + 3600000L, event.endAt + 3600000L,
                event.registrationDeadline, 3, event.category, SourceType.MANUAL,
                "Verified integration source", "https://www.buaa.edu.cn/");
        EventRecord updated = await(graph.admin.update(event.id, event.version, changed));
        switchUser(2);
        list();
        waitFor("edited list", a -> card(a, updated.title, "Updated room"));
        scenario.onActivity(a -> a.open(Route.event(ScreenKey.EVENT_DETAIL, event.id)));
        waitFor("detail", a -> content(a, "Updated description")
                && content(a, "Verified integration source"));
        scenario.recreate();
        waitFor("detail recreation", a -> content(a, updated.title)
                && content(a, "Updated room"));

        switchUser(1);
        await(graph.admin.cancel(event.id, updated.version));
        switchUser(2);
        list();
        waitFor("cancelled activity removed from recommendation", a ->
                card(a, updated.title, "已取消") && !recommended(a, updated.title));
    }

    private EventRecord create(String label, int capacity, boolean closed) throws Exception {
        long now = System.currentTimeMillis();
        long start = now + TimeUnit.DAYS.toMillis(7);
        EventRecord event = await(graph.admin.create(new EventInput(
                "browse-integration-" + label + "-" + System.nanoTime(),
                "Integration description", "Integration room", start, start + 7200000L,
                closed ? now - 60000L : start - 3600000L, capacity, "TECH",
                SourceType.MANUAL, "Integration test", "")));
        created.add(event.id);
        return event;
    }

    private void switchUser(long userId) throws Exception {
        await(graph.platform.sessionApi().switchDemoUser(userId));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private void list() {
        scenario.onActivity(a -> a.findViewById(R.id.shell_events).performClick());
    }

    private static boolean card(MainActivity activity, String title, String... parts) {
        View root = activity.findViewById(com.example.campusevents.browse.R.id.browse_rows);
        return matchingText(root, text -> text.contains(title)
                && Arrays.stream(parts).allMatch(text::contains));
    }

    private static boolean recommended(MainActivity activity, String title) {
        View root = activity.findViewById(com.example.campusevents.browse.R.id.browse_recommendation_rows);
        return matchingText(root, text -> text.contains(title) && text.contains("当前可报名"));
    }

    private static boolean content(MainActivity activity, String text) {
        return matchingText(activity.findViewById(com.example.campusevents.browse.R.id.browse_rows),
                value -> value.contains(text));
    }

    private static boolean matchingText(View view, Predicate<String> condition) {
        if (view == null) return false;
        if (view instanceof TextView && condition.test(((TextView) view).getText().toString())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (matchingText(group.getChildAt(i), condition)) return true;
            }
        }
        return false;
    }

    private void waitFor(String message, Predicate<MainActivity> condition) {
        long deadline = SystemClock.elapsedRealtime() + 15000L;
        AtomicBoolean pass = new AtomicBoolean();
        do {
            scenario.onActivity(a -> pass.set(condition.test(a)));
            if (pass.get()) return;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail(message);
    }

    private static <T> T await(AsyncCall<T> call) throws Exception {
        ApiResult<T> result = awaitResult(call);
        assertTrue(result.code + " " + result.message, result.success);
        return result.data;
    }

    private static <T> ApiResult<T> awaitResult(AsyncCall<T> call) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        call.enqueue(value -> { result.set(value); latch.countDown(); });
        assertTrue("Missing callback", latch.await(15, TimeUnit.SECONDS));
        return result.get();
    }
}
