package com.example.campusevents;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.*;

import com.example.campusevents.bootstrap.AppGraph;
import com.example.campusevents.contract.common.*;
import com.example.campusevents.contract.session.*;
import com.example.campusevents.platform.invalidation.*;
import com.example.campusevents.platform.ui.*;

import java.util.*;

public final class MainActivity extends AppCompatActivity {
    private AppGraph graph;
    private SessionSnapshot session;
    private final List<UserSnapshot> users = new ArrayList<>();
    private final List<CallHandle> calls = new ArrayList<>();
    private DataInvalidation.Subscription subscription;
    private boolean switching;
    private boolean checkingSession;
    private boolean resetRootAfterSession;
    private long sessionRequest;
    private long pendingEventId;
    private long pendingRecipient;
    private Route deferredRoute;
    private boolean deferredRoot;
    private long restoredEpoch = -1;

    @Override protected void onCreate(Bundle savedState) {
        graph = ((CampusApplication) getApplication()).graph();
        graph.navigator.bind(this);
        getSupportFragmentManager().setFragmentFactory(graph.ui.fragmentFactory());
        super.onCreate(savedState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        boolean lightTheme = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                != Configuration.UI_MODE_NIGHT_YES;
        WindowInsetsControllerCompat systemBars = new WindowInsetsControllerCompat(
                getWindow(), findViewById(R.id.shell_main));
        systemBars.setAppearanceLightStatusBars(lightTheme);
        systemBars.setAppearanceLightNavigationBars(lightTheme);
        int contentPadding = Math.round(12 * getResources().getDisplayMetrics().density);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.shell_main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left + contentPadding, bars.top + contentPadding,
                    bars.right + contentPadding, bars.bottom + contentPadding);
            return insets;
        });
        if (savedState != null) {
            restoredEpoch = savedState.getLong("screenEpoch", -1);
            pendingEventId = savedState.getLong("pendingEventId");
            pendingRecipient = savedState.getLong("pendingRecipient");
            String screen = savedState.getString("deferredScreen");
            if (screen != null) {
                ScreenKey key = ScreenKey.valueOf(screen);
                long id = savedState.getLong("deferredEventId");
                deferredRoute = id > 0 ? Route.event(key, id) : Route.to(key);
                deferredRoot = savedState.getBoolean("deferredRoot");
            }
        } else {
            captureNotification(getIntent());
        }
        findViewById(R.id.shell_events).setOnClickListener(v -> root(ScreenKey.EVENT_LIST));
        findViewById(R.id.shell_registrations).setOnClickListener(v -> root(ScreenKey.MY_REGISTRATIONS));
        findViewById(R.id.shell_favorites).setOnClickListener(v -> root(ScreenKey.MY_FAVORITES));
        findViewById(R.id.shell_admin).setOnClickListener(v -> root(ScreenKey.ADMIN_LIST));
        subscription = graph.platform.invalidation().subscribe(change -> {
            if (change.topic == ChangeTopic.SESSION) {
                loadSession(true);
            }
        });
        calls.add(graph.platform.sessionApi().listDemoUsers().enqueue(result -> {
            if (!result.success) {
                status(result.message);
                return;
            }
            users.clear();
            users.addAll(result.data);
            loadSession(false);
        }));
    }

    private void loadSession(boolean switched) {
        resetRootAfterSession |= switched;
        checkingSession = true;
        long request = ++sessionRequest;
        calls.add(graph.platform.sessionApi().current().enqueue(result -> {
            if (request != sessionRequest) {
                return;
            }
            checkingSession = false;
            if (!result.success) {
                status(result.message);
                return;
            }
            if (restoredEpoch >= 0 && restoredEpoch != result.data.epoch) {
                resetRootAfterSession = true;
            }
            restoredEpoch = -1;
            session = result.data;
            renderIdentity();
            if (resetRootAfterSession) {
                resetRootAfterSession = false;
                root(ScreenKey.EVENT_LIST);
            } else if (getSupportFragmentManager().findFragmentById(R.id.shell_screen) == null) {
                root(ScreenKey.EVENT_LIST);
            }
            openPendingNotification();
        }));
    }

    private void renderIdentity() {
        Spinner spinner = findViewById(R.id.shell_identity);
        spinner.setOnItemSelectedListener(null);
        List<String> names = new ArrayList<>();
        names.add("请选择演示身份");
        int selected = 0;
        for (int i = 0; i < users.size(); i++) {
            UserSnapshot u = users.get(i);
            names.add(u.displayName + (u.role == Role.ADMIN ? " · 管理员" : " · 学生"));
            if (session.user != null && session.user.userId == u.userId) {
                selected = i + 1;
            }
        }
        spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
        spinner.setSelection(selected);
        status(session.user == null ? "请选择身份后操作，活动可公开浏览" : "当前身份  " + session.user.displayName);
        findViewById(R.id.shell_admin).setVisibility(
                session.user != null && session.user.role == Role.ADMIN ? View.VISIBLE : View.GONE);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onNothingSelected(AdapterView<?> parent) { }

            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0 || switching) {
                    return;
                }
                UserSnapshot next = users.get(position - 1);
                if (session.user != null && session.user.userId == next.userId) {
                    return;
                }
                switching = true;
                spinner.setEnabled(false);
                calls.add(graph.platform.sessionApi().switchDemoUser(next.userId).enqueue(result -> {
                    switching = false;
                    spinner.setEnabled(true);
                    if (!result.success) {
                        status(result.message);
                        renderIdentity();
                    } else {
                        openPendingNotification();
                    }
                }));
            }
        });
    }

    private void root(ScreenKey screen) {
        if (getSupportFragmentManager().isStateSaved()) {
            deferredRoute = Route.to(screen);
            deferredRoot = true;
            return;
        }
        getSupportFragmentManager().popBackStackImmediate(
                null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        display(Route.to(screen), false);
    }

    public void open(Route route) {
        display(route, true);
    }

    private boolean display(Route route, boolean backStack) {
        if (getSupportFragmentManager().isStateSaved()) {
            deferredRoute = route;
            deferredRoot = !backStack;
            return false;
        }
        androidx.fragment.app.FragmentTransaction tx = getSupportFragmentManager().beginTransaction()
                .replace(R.id.shell_screen, graph.ui.createScreen(route));
        if (backStack) {
            tx.addToBackStack(route.screen.name());
        }
        tx.commit();
        return true;
    }

    private void status(String message) {
        ((TextView) findViewById(R.id.shell_session_status)).setText(message);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        captureNotification(intent);
        loadSession(false);
    }

    private void captureNotification(Intent intent) {
        if (intent == null) {
            return;
        }
        pendingEventId = intent.getLongExtra("eventId", 0);
        pendingRecipient = intent.getLongExtra("recipientUserId", 0);
        intent.removeExtra("eventId");
        intent.removeExtra("recipientUserId");
    }

    private void openPendingNotification() {
        if (pendingEventId <= 0 || session == null || switching || checkingSession) {
            return;
        }
        if (session.user == null || session.user.userId != pendingRecipient) {
            status("请先选择通知所属身份，随后进入活动");
            return;
        }
        if (getSupportFragmentManager().isStateSaved()) {
            return;
        }
        if (display(Route.event(ScreenKey.EVENT_DETAIL, pendingEventId), true)) {
            pendingEventId = 0;
            pendingRecipient = 0;
        }
    }

    @Override protected void onPostResume() {
        super.onPostResume();
        Route route = deferredRoute;
        boolean replaceRoot = deferredRoot;
        deferredRoute = null;
        if (route != null) {
            if (replaceRoot) {
                root(route.screen);
            } else {
                display(route, true);
            }
        }
        openPendingNotification();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("pendingEventId", pendingEventId);
        state.putLong("pendingRecipient", pendingRecipient);
        state.putLong("screenEpoch", session == null ? -1 : session.epoch);
        if (deferredRoute != null) {
            state.putString("deferredScreen", deferredRoute.screen.name());
            state.putLong("deferredEventId", deferredRoute.args.eventId);
            state.putBoolean("deferredRoot", deferredRoot);
        }
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        if (subscription != null) {
            subscription.unsubscribe();
        }
        for (CallHandle call : calls) {
            call.cancelDelivery();
        }
        graph.navigator.unbind(this);
        super.onDestroy();
    }
}
