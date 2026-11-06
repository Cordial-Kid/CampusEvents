package com.example.campusevents;

import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility.GONE;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.campusevents.bootstrap.AppGraph;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.AsyncCall;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventInput;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.session.SessionSnapshot;
import com.example.campusevents.platform.effect.EffectKey;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.ui.Route;
import com.example.campusevents.platform.ui.RouteArgs;
import com.example.campusevents.platform.ui.ScreenKey;
import com.example.campusevents.ui.DetailFragment;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/** Exercises the real composition root without requiring C/D's pending feature implementations. */
@RunWith(AndroidJUnit4.class)
public final class ShellIntegrationTest {
    private static final long TIMEOUT_MS = 10_000;
    private ActivityScenario<MainActivity> scenario;
    private AppGraph graph;
    private SessionSnapshot originalSession;
    private long createdEventId;

    @Before public void launchApplication() throws Exception {
        CampusApplication application = (CampusApplication) InstrumentationRegistry
                .getInstrumentation().getTargetContext().getApplicationContext();
        graph = application.graph();
        originalSession = await(graph.platform.sessionApi().current());
        scenario = ActivityScenario.launch(MainActivity.class);
        waitForActivity("Identity choices were not loaded", activity -> {
            Spinner identity = activity.findViewById(R.id.shell_identity);
            return identity.getAdapter() != null && identity.getAdapter().getCount() == 4;
        });
    }

    @After public void restoreDemoData() throws Exception {
        if (scenario != null) scenario.close();
        if (graph == null) return;
        if (createdEventId > 0) {
            // Cleanup is limited to this test's own newly created row and uses the shared queue.
            await(graph.platform.dataAccess().write(tx -> {
                tx.db().delete("events", "id=?", new String[]{Long.toString(createdEventId)});
                tx.invalidate(ChangeTopic.EVENT_CONTENT, createdEventId);
                return null;
            }));
        }
        if (originalSession == null) return;
        if (originalSession.user != null) {
            await(graph.platform.sessionApi().switchDemoUser(originalSession.user.userId));
        } else {
            // There is deliberately no public logout API for the demonstration selector.
            ApiResult<Void> cleanup = awaitResult(graph.platform.dataAccess().<Void>write(tx -> {
                tx.db().execSQL("UPDATE app_session SET currentUserId=NULL,epoch=epoch+1 WHERE singletonId=1");
                tx.requestEffect(EffectKey.REMINDER_RECONCILE);
                tx.invalidate(ChangeTopic.SESSION, 0);
                return null;
            }));
            // A generic command retains its captured epoch, even when this test changes the session.
            assertEquals(ResultCode.SESSION_CHANGED, cleanup.code);
            assertNull(await(graph.platform.sessionApi().current()).user);
        }
    }

    @Test public void identitySelectionControlsAdministratorEntry() {
        selectIdentity("管理员 · 管理员", true);
        onView(withId(R.id.shell_admin)).check(matches(isDisplayed()));
        selectIdentity("学生甲 · 学生", false);
        onView(withId(R.id.shell_admin)).check(matches(withEffectiveVisibility(GONE)));
        waitForActivity("Student event screen was not restored after switching", activity ->
                activity.getSupportFragmentManager().findFragmentById(R.id.shell_screen) != null);
    }

    @Test public void realCreateAndTextEditAppearInAdministratorList() throws Exception {
        selectIdentity("管理员 · 管理员", true);
        String originalTitle = "shell-test-" + System.nanoTime();
        long startAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7);
        EventInput input = new EventInput(originalTitle, "Shell integration test", "Test classroom",
                startAt, startAt + TimeUnit.HOURS.toMillis(2),
                startAt - TimeUnit.DAYS.toMillis(1), 20, "TECH", SourceType.MANUAL,
                "Instrumentation test data", "");
        EventRecord created = await(graph.admin.create(input));
        createdEventId = created.id;
        assertTrue(createdEventId > 0);
        assertEquals(1, created.version);

        onView(withId(R.id.shell_admin)).perform(click());
        waitForActivity("Created event did not appear in administrator list", activity ->
                containsText(activity.findViewById(com.example.campusevents.event.R.id.event_rows), originalTitle));

        String editedTitle = originalTitle + "-edited";
        EventInput editedInput = new EventInput(editedTitle, created.description, created.location,
                created.startAt, created.endAt, created.registrationDeadline, created.capacity,
                created.category, created.sourceType, created.sourceName, created.sourceUrl);
        EventRecord edited = await(graph.admin.update(created.id, created.version, editedInput));
        assertEquals(created.version + 1, edited.version);
        assertEquals(editedTitle, await(graph.events.readApi().get(created.id)).title);

        onView(withId(com.example.campusevents.event.R.id.event_refresh)).perform(click());
        waitForActivity("Edited title did not appear after refreshing administrator list", activity -> {
            View rows = activity.findViewById(com.example.campusevents.event.R.id.event_rows);
            return containsText(rows, editedTitle) && !containsText(rows, originalTitle);
        });
    }

    @Test public void detailPanelsKeepTheirEventAcrossActivityRecreation() throws Exception {
        selectIdentity("学生甲 · 学生", false);
        List<EventRecord> events = await(graph.events.readApi().list(new EventFilter()));
        assertTrue("The first-install demonstration events must exist", !events.isEmpty());
        long eventId = events.get(0).id;
        scenario.onActivity(activity -> activity.open(Route.event(ScreenKey.EVENT_DETAIL, eventId)));
        waitForActivity("Detail panels were not attached", activity -> panelsReady(activity, eventId));
        assertDetailPanels(eventId);

        scenario.recreate();
        waitForActivity("Detail panels did not survive activity recreation", activity -> panelsReady(activity, eventId));
        assertDetailPanels(eventId);
    }

    @Test public void backgroundIdentityCallbackCanResumeRootNavigation() throws Exception {
        selectIdentity("学生甲 · 学生", false);
        scenario.moveToState(Lifecycle.State.CREATED);
        SessionSnapshot administrator = await(graph.platform.sessionApi().switchDemoUser(1));
        assertNotNull(administrator.user);
        assertEquals(1, administrator.user.userId);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        scenario.moveToState(Lifecycle.State.RESUMED);
        waitForActivity("Background identity change was not reflected on resume", activity -> {
            View admin = activity.findViewById(R.id.shell_admin);
            Spinner identity = activity.findViewById(R.id.shell_identity);
            return activity.hasWindowFocus() && admin.getVisibility() == View.VISIBLE
                    && "管理员 · 管理员".equals(identity.getSelectedItem())
                    && activity.getSupportFragmentManager().findFragmentById(R.id.shell_screen) != null;
        });
        // Exercise the real listener after background recovery, avoiding API 37's cross-window touch loss.
        scenario.onActivity(activity -> assertTrue(activity.findViewById(R.id.shell_admin).performClick()));
        waitForActivity("Administrator navigation failed after a background session callback", activity ->
                activity.findViewById(com.example.campusevents.event.R.id.event_create) != null);
    }

    private void selectIdentity(String label, boolean administrator) {
        onView(withId(R.id.shell_identity)).perform(click());
        onData(equalTo(label)).perform(click());
        String displayName = label.substring(0, label.indexOf(" · "));
        waitForActivity("Identity selection was not applied", activity -> {
            Spinner spinner = activity.findViewById(R.id.shell_identity);
            View admin = activity.findViewById(R.id.shell_admin);
            TextView status = activity.findViewById(R.id.shell_session_status);
            return spinner.isEnabled() && label.equals(spinner.getSelectedItem())
                    && status.getText().toString().contains(displayName)
                    && (admin.getVisibility() == View.VISIBLE) == administrator;
        });
    }

    private void assertDetailPanels(long eventId) {
        scenario.onActivity(activity -> {
            Fragment detail = activity.getSupportFragmentManager().findFragmentById(R.id.shell_screen);
            assertTrue(detail instanceof DetailFragment);
            assertEquals(eventId, detail.requireArguments().getLong(RouteArgs.EVENT_ID));
            FragmentManager children = detail.getChildFragmentManager();
            assertEquals(3, children.getFragments().size());
            int[] slots = {R.id.shell_detail_content_slot, R.id.shell_detail_registration_slot,
                    R.id.shell_detail_engagement_slot};
            for (int slot : slots) {
                Fragment panel = children.findFragmentById(slot);
                assertNotNull(panel);
                assertTrue(panel.isAdded());
                assertEquals(eventId, panel.requireArguments().getLong(RouteArgs.EVENT_ID));
            }
        });
    }

    private static boolean panelsReady(MainActivity activity, long eventId) {
        Fragment detail = activity.getSupportFragmentManager().findFragmentById(R.id.shell_screen);
        if (!(detail instanceof DetailFragment) || detail.getArguments() == null
                || detail.getArguments().getLong(RouteArgs.EVENT_ID) != eventId) return false;
        FragmentManager children = detail.getChildFragmentManager();
        return children.findFragmentById(R.id.shell_detail_content_slot) != null
                && children.findFragmentById(R.id.shell_detail_registration_slot) != null
                && children.findFragmentById(R.id.shell_detail_engagement_slot) != null;
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView && expected.contentEquals(((TextView) view).getText())) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected)) return true;
            }
        }
        return false;
    }

    private void waitForActivity(String message, Predicate<MainActivity> condition) {
        long deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS;
        AtomicBoolean satisfied = new AtomicBoolean();
        do {
            scenario.onActivity(activity -> satisfied.set(condition.test(activity)));
            if (satisfied.get()) return;
            SystemClock.sleep(25);
        } while (SystemClock.elapsedRealtime() < deadline);
        AtomicReference<String> diagnostics = new AtomicReference<>();
        scenario.onActivity(activity -> {
            FragmentManager fragments = activity.getSupportFragmentManager();
            Fragment current = fragments.findFragmentById(R.id.shell_screen);
            Spinner identity = activity.findViewById(R.id.shell_identity);
            TextView status = activity.findViewById(R.id.shell_session_status);
            View admin = activity.findViewById(R.id.shell_admin);
            diagnostics.set("fragment=" + (current == null ? "null" : current.getClass().getName())
                    + ", fragmentResumed=" + (current != null && current.isResumed())
                    + ", stateSaved=" + fragments.isStateSaved()
                    + ", lifecycle=" + activity.getLifecycle().getCurrentState()
                    + ", status=" + status.getText()
                    + ", identity=" + identity.getSelectedItem()
                    + ", administratorVisibility=" + admin.getVisibility()
                    + ", administratorClickable=" + admin.isClickable()
                    + ", windowFocus=" + activity.hasWindowFocus()
                    + ", createView=" + activity.findViewById(com.example.campusevents.event.R.id.event_create));
        });
        fail(message + " [" + diagnostics.get() + "]");
    }

    private static <T> T await(AsyncCall<T> call) throws Exception {
        ApiResult<T> value = awaitResult(call);
        assertTrue(value.code + " " + value.message, value.success);
        return value.data;
    }

    private static <T> ApiResult<T> awaitResult(AsyncCall<T> call) throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        AtomicReference<ApiResult<T>> result = new AtomicReference<>();
        call.enqueue(value -> {
            result.set(value);
            completed.countDown();
        });
        assertTrue("API callback did not arrive", completed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        return result.get();
    }
}
