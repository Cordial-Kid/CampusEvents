package com.example.campusevents.browse;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import com.example.campusevents.contract.browse.BrowseTimeline;
import com.example.campusevents.contract.browse.EventBrowseApi;
import com.example.campusevents.contract.browse.EventView;
import com.example.campusevents.contract.common.ApiResult;
import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.registration.RegistrationSummary;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.platform.invalidation.ChangeTopic;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.Route;
import com.example.campusevents.platform.ui.RouteArgs;
import com.example.campusevents.platform.ui.ScreenKey;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class BrowseFragment extends Fragment {
    private static final String[] CATEGORIES = {"TECH", "SPORT", "ACADEMIC"};
    private static final String DATE = "yyyy-MM-dd";
    private static final String DATE_TIME = "yyyy-MM-dd HH:mm";
    private static final String TIME = "HH:mm";
    private static final String QUERY_STATE = "browse.query";
    private static final String CATEGORY_STATE = "browse.category";
    private static final String PREFERENCES = "browse_interests";

    private final EventBrowseApi api;
    private final DataInvalidation invalidation;
    private final Navigator navigator;
    private BrowseViewModel model;
    private DataInvalidation.Subscription subscription;
    private AlertDialog interestsDialog;
    private long eventId;
    private String keyword = "";
    private int categoryIndex;

    public BrowseFragment(EventBrowseApi api, DataInvalidation invalidation, Navigator navigator) {
        super(R.layout.browse_content);
        this.api = api;
        this.invalidation = invalidation;
        this.navigator = navigator;
    }

    @Override public void onViewCreated(View view, Bundle state) {
        eventId = RouteArgs.fromBundle(getArguments()).eventId;
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) {
                return type.cast(new BrowseViewModel(api));
            }
        }).get(BrowseViewModel.class);

        view.findViewById(R.id.browse_refresh).setOnClickListener(button -> reload());
        if (eventId == 0) {
            configureTimeline(view, state);
            model.timeline.observe(getViewLifecycleOwner(), result -> renderTimeline(view, result));
        } else {
            model.detail.observe(getViewLifecycleOwner(), result -> renderDetail(view, result));
        }
        subscription = invalidation.subscribe(change -> {
            if (change.topic == ChangeTopic.SESSION) {
                dismissInterests();
                model.clear();
            }
            if (eventId == 0 || change.eventId == 0 || change.eventId == eventId
                    || change.topic == ChangeTopic.SESSION) {
                reload();
            }
        });
    }

    private void configureTimeline(View view, Bundle state) {
        view.findViewById(R.id.browse_filters).setVisibility(View.VISIBLE);
        EditText search = view.findViewById(R.id.browse_search);
        Spinner category = view.findViewById(R.id.browse_category);
        String[] labels = getResources().getStringArray(R.array.browse_category_filter);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        category.setAdapter(adapter);
        if (state != null) {
            keyword = state.getString(QUERY_STATE, "");
            categoryIndex = state.getInt(CATEGORY_STATE, 0);
        }
        search.setText(keyword);
        category.setSelection(categoryIndex);
        View.OnClickListener applyFilter = button -> {
            keyword = search.getText().toString().trim();
            categoryIndex = Math.max(0, category.getSelectedItemPosition());
            model.clear();
            reload();
        };
        view.findViewById(R.id.browse_apply_filter).setOnClickListener(applyFilter);
        search.setOnEditorActionListener((input, action, key) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            applyFilter.onClick(input);
            return true;
        });
    }

    @Override public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(QUERY_STATE, keyword);
        outState.putInt(CATEGORY_STATE, categoryIndex);
    }

    @Override public void onResume() {
        super.onResume();
        if (model != null) {
            model.clear();
            reload();
        }
    }

    private void reload() {
        if (eventId > 0) {
            model.loadDetail(eventId);
        } else {
            String category = categoryIndex > 0 ? CATEGORIES[categoryIndex - 1] : null;
            model.loadTimeline(new EventFilter(null, null, category,
                    keyword.isEmpty() ? null : keyword, true));
        }
    }

    private void renderTimeline(View view, ApiResult<BrowseTimeline> result) {
        LinearLayout rows = view.findViewById(R.id.browse_rows);
        rows.removeAllViews();
        View recommendations = view.findViewById(R.id.browse_recommendations);
        recommendations.setVisibility(View.GONE);
        TextView status = view.findViewById(R.id.browse_status);
        if (!showResultStatus(status, result)) return;

        BrowseTimeline timeline = result.data;
        boolean student = timeline.viewer != null && timeline.viewer.role == Role.STUDENT;
        if (student) {
            renderRecommendations(view, timeline);
        }
        if (timeline.events.isEmpty()) {
            status.setText(R.string.browse_empty);
            return;
        }
        String previousDate = "";
        for (EventView item : timeline.events) {
            String date = BrowsePresentation.format(item.event.startAt, DATE);
            if (!date.equals(previousDate)) {
                addHeading(rows, date);
                previousDate = date;
            }
            addActivity(rows, item, activitySummary(item, student));
        }
    }

    private void renderDetail(View view, ApiResult<EventView> result) {
        LinearLayout rows = view.findViewById(R.id.browse_rows);
        rows.removeAllViews();
        if (!showResultStatus(view.findViewById(R.id.browse_status), result)) return;

        EventView item = result.data;
        EventRecord event = item.event;
        addHeading(rows, event.title);
        addText(rows, getString(R.string.browse_detail_meta,
                BrowsePresentation.format(event.startAt, DATE_TIME),
                BrowsePresentation.format(event.endAt, DATE_TIME),
                event.location, categoryLabel(event.category), statusLabel(item),
                BrowsePresentation.format(event.registrationDeadline, DATE_TIME)));
        addText(rows, event.description);
        int sourceType = event.sourceType == SourceType.OFFICIAL
                ? R.string.browse_source_official : R.string.browse_source_manual;
        addText(rows, getString(R.string.browse_source, event.sourceName, getString(sourceType)));
        Uri source = Uri.parse(event.sourceUrl);
        if (("https".equalsIgnoreCase(source.getScheme()) || "http".equalsIgnoreCase(source.getScheme()))
                && source.getHost() != null) {
            Button link = new Button(requireContext());
            link.setText(R.string.browse_open_source);
            link.setOnClickListener(button -> openSource(source));
            rows.addView(link);
        }
    }

    private boolean showResultStatus(TextView status, ApiResult<?> result) {
        if (result == null) {
            status.setText(R.string.browse_loading);
        } else if (result.code == ResultCode.SESSION_CHANGED) {
            status.setText(R.string.browse_session_changed);
        } else if (!result.success) {
            status.setText(result.message == null || result.message.isEmpty()
                    ? getString(R.string.browse_load_failed) : result.message);
        } else {
            status.setText("");
            return true;
        }
        return false;
    }

    private String activitySummary(EventView item, boolean student) {
        EventRecord event = item.event;
        String endPattern = BrowsePresentation.format(event.startAt, DATE)
                .equals(BrowsePresentation.format(event.endAt, DATE)) ? TIME : DATE_TIME;
        return getString(R.string.browse_activity_summary, event.title,
                BrowsePresentation.format(event.startAt, TIME),
                BrowsePresentation.format(event.endAt, endPattern),
                event.location, categoryLabel(event.category), statusLabel(item),
                registrationLabel(item, student));
    }

    private String registrationLabel(EventView item, boolean student) {
        if (item.registration.state != ModuleValue.State.READY) {
            return getString(R.string.browse_registration_pending);
        }
        RegistrationSummary registration = item.registration.data;
        if (!student) {
            return getString(R.string.browse_registration_count, registration.activeCount,
                    registration.remainingCapacity);
        }
        int personal;
        switch (registration.myStatus) {
            case REGISTERED: personal = R.string.browse_registered; break;
            case CANCELLED: personal = R.string.browse_registration_cancelled; break;
            default: personal = R.string.browse_not_registered; break;
        }
        return getString(R.string.browse_registration, registration.activeCount,
                registration.remainingCapacity, getString(personal));
    }

    private String statusLabel(EventView item) {
        switch (BrowsePresentation.status(item)) {
            case CANCELLED: return getString(R.string.browse_cancelled);
            case ENDED: return getString(R.string.browse_ended);
            case STARTED: return getString(R.string.browse_started);
            case REGISTRATION_CLOSED: return getString(R.string.browse_registration_closed);
            default: return getString(R.string.browse_upcoming);
        }
    }

    private String categoryLabel(String code) {
        String[] labels = getResources().getStringArray(R.array.browse_category_filter);
        for (int index = 0; index < CATEGORIES.length; index++) {
            if (CATEGORIES[index].equals(code)) return labels[index + 1];
        }
        return code;
    }

    private void renderRecommendations(View view, BrowseTimeline timeline) {
        long userId = timeline.viewer.userId;
        Set<String> interests = readInterests(userId);
        view.findViewById(R.id.browse_recommendations).setVisibility(View.VISIBLE);
        view.findViewById(R.id.browse_edit_interests)
                .setOnClickListener(button -> editInterests(userId, interests));
        LinearLayout recommendations = view.findViewById(R.id.browse_recommendation_rows);
        recommendations.removeAllViews();
        if (interests.isEmpty()) {
            addText(recommendations, getString(R.string.browse_interests_prompt));
            return;
        }
        List<EventView> matches = BrowsePresentation.recommend(timeline.events, interests);
        if (matches.isEmpty()) {
            boolean pending = timeline.events.stream().anyMatch(item ->
                    interests.contains(item.event.category)
                            && BrowsePresentation.status(item) == BrowsePresentation.Status.UPCOMING
                            && item.registration.state == ModuleValue.State.NOT_READY);
            addText(recommendations, getString(pending
                    ? R.string.browse_recommendations_pending : R.string.browse_recommendations_empty));
            return;
        }
        for (EventView item : matches) {
            addActivity(recommendations, item, getString(R.string.browse_recommendation,
                    item.event.title, BrowsePresentation.format(item.event.startAt, DATE_TIME),
                    categoryLabel(item.event.category)));
        }
    }

    private void editInterests(long userId, Set<String> current) {
        Set<String> selected = new HashSet<>(current);
        String[] labels = new String[CATEGORIES.length];
        boolean[] checked = new boolean[CATEGORIES.length];
        for (int index = 0; index < CATEGORIES.length; index++) {
            labels[index] = categoryLabel(CATEGORIES[index]);
            checked[index] = selected.contains(CATEGORIES[index]);
        }
        dismissInterests();
        interestsDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.browse_edit_interests)
                .setMultiChoiceItems(labels, checked, (dialog, index, isChecked) -> {
                    if (isChecked) selected.add(CATEGORIES[index]);
                    else selected.remove(CATEGORIES[index]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.browse_save, (dialog, which) -> {
                    preferences().edit().putStringSet(Long.toString(userId), selected).apply();
                    reload();
                })
                .show();
    }

    private Set<String> readInterests(long userId) {
        return new HashSet<>(preferences().getStringSet(Long.toString(userId), Collections.emptySet()));
    }

    private SharedPreferences preferences() {
        return requireContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    private void dismissInterests() {
        if (interestsDialog != null) {
            interestsDialog.dismiss();
            interestsDialog = null;
        }
    }

    private void openSource(Uri source) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, source));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), R.string.browse_no_browser, Toast.LENGTH_SHORT).show();
        }
    }

    private void addHeading(LinearLayout container, String text) {
        TextView heading = addText(container, text);
        heading.setTextAppearance(android.R.style.TextAppearance_Material_Title);
        ViewCompat.setAccessibilityHeading(heading, true);
    }

    private TextView addText(LinearLayout container, String text) {
        TextView label = new TextView(requireContext());
        label.setTextAppearance(android.R.style.TextAppearance_Material_Body1);
        label.setText(text);
        int padding = Math.round(8 * getResources().getDisplayMetrics().density);
        label.setPadding(0, padding, 0, padding);
        container.addView(label);
        return label;
    }

    private void addActivity(LinearLayout container, EventView item, String text) {
        Button button = new Button(requireContext(), null, android.R.attr.borderlessButtonStyle);
        button.setAllCaps(false);
        button.setText(text);
        button.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        button.setOnClickListener(view -> navigator.open(Route.event(ScreenKey.EVENT_DETAIL, item.event.id)));
        container.addView(button, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    @Override public void onDestroyView() {
        dismissInterests();
        if (subscription != null) {
            subscription.unsubscribe();
            subscription = null;
        }
        if (model != null) model.cancel();
        super.onDestroyView();
    }
}
