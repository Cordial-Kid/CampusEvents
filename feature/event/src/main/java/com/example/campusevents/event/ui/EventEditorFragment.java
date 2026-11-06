package com.example.campusevents.event.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.event.EventAdminApi;
import com.example.campusevents.contract.event.EventInput;
import com.example.campusevents.contract.event.EventReadApi;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.event.R;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.RouteArgs;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public final class EventEditorFragment extends Fragment {
    private static final String[] FIELD_KEYS = {"title", "description", "location", "category", "start", "end", "deadline", "capacity", "sourceName", "sourceUrl"};
    private static final int[] FIELD_IDS = {R.id.event_input_title, R.id.event_input_description,
            R.id.event_input_location, R.id.event_input_category, R.id.event_input_start, R.id.event_input_end,
            R.id.event_input_deadline, R.id.event_input_capacity, R.id.event_input_source_name, R.id.event_input_source_url};
    private final EventReadApi reads;
    private final EventAdminApi admin;
    private final Navigator navigator;
    private final SimpleDateFormat dateFormat;
    private EventEditorViewModel model;
    private EditText[] fields;
    private Spinner sourceType;
    private Button save;
    private TextView status;
    private CallHandle loadCall;
    private long eventId;
    private AlertDialog activeDialog;

    public EventEditorFragment(EventReadApi reads, EventAdminApi admin, Navigator navigator) {
        this.reads = reads;
        this.admin = admin;
        this.navigator = navigator;
        dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        dateFormat.setLenient(false);
    }

    public static EventEditorFragment create(EventReadApi reads, EventAdminApi admin, Navigator navigator, long eventId) {
        EventEditorFragment fragment = new EventEditorFragment(reads, admin, navigator);
        Bundle args = new Bundle();
        args.putLong(RouteArgs.EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        eventId = getArguments() == null ? 0 : getArguments().getLong(RouteArgs.EVENT_ID, 0);
        model = new ViewModelProvider(this).get(EventEditorViewModel.class);
        if (state != null && model.draft == null) {
            model.draft = state.getBundle("eventDraft");
            model.expectedVersion = state.getLong("eventVersion");
            model.originalStart = state.getLong("eventOriginalStart");
            model.originalEnd = state.getLong("eventOriginalEnd");
            model.originalDeadline = state.getLong("eventOriginalDeadline");
        }
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle state) {
        View view = inflater.inflate(R.layout.event_editor, parent, false);
        ((TextView) view.findViewById(R.id.event_editor_heading)).setText(eventId == 0 ? "新增活动" : "编辑活动");
        fields = new EditText[FIELD_IDS.length];
        for (int i = 0; i < fields.length; i++) fields[i] = view.findViewById(FIELD_IDS[i]);
        sourceType = view.findViewById(R.id.event_input_source_type);
        sourceType.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, SourceType.values()));
        save = view.findViewById(R.id.event_save);
        status = view.findViewById(R.id.event_editor_status);
        save.setOnClickListener(v -> submit());
        Button reload = view.findViewById(R.id.event_reload);
        reload.setVisibility(eventId == 0 ? View.GONE : View.VISIBLE);
        reload.setOnClickListener(v -> activeDialog = new AlertDialog.Builder(requireContext()).setTitle("重新读取")
                .setMessage("重新读取会放弃当前尚未保存的编辑。")
                .setNegativeButton("返回", null).setPositiveButton("重新读取", (dialog, which) -> load()).show());
        if (model.draft != null) restoreDraft();
        else if (eventId == 0) initializeCreate();
        else load();
        model.onChanged = this::renderSaveState;
        return view;
    }

    @Override public void onResume() {
        super.onResume();
        renderSaveState();
    }

    private void initializeCreate() {
        long start = System.currentTimeMillis() + 3L * 24 * 60 * 60 * 1000;
        fields[3].setText("TECH");
        fields[4].setText(format(start));
        fields[5].setText(format(start + 2 * 60 * 60 * 1000));
        fields[6].setText(format(start - 60 * 60 * 1000));
        fields[7].setText("20");
        fields[8].setText("管理员人工录入");
        sourceType.setSelection(SourceType.MANUAL.ordinal());
        captureDraft();
    }

    private void load() {
        if (model.saving) return;
        if (loadCall != null) loadCall.cancelDelivery();
        model.saveResult = null;
        save.setEnabled(false);
        status.setText("正在读取活动…");
        loadCall = reads.get(eventId).enqueue(result -> {
            if (fields == null) return;
            if (!result.success) {
                status.setText(result.code + " · " + result.message);
                return;
            }
            EventRecord event = result.data;
            model.expectedVersion = event.version;
            model.originalStart = event.startAt;
            model.originalEnd = event.endAt;
            model.originalDeadline = event.registrationDeadline;
            String[] values = {event.title, event.description, event.location, event.category,
                    format(event.startAt), format(event.endAt), format(event.registrationDeadline),
                    Integer.toString(event.capacity), event.sourceName, event.sourceUrl};
            for (int i = 0; i < fields.length; i++) fields[i].setText(values[i]);
            sourceType.setSelection(event.sourceType.ordinal());
            captureDraft();
            save.setEnabled(true);
            status.setText("正在编辑版本 " + event.version);
        });
    }

    private void submit() {
        if (model.saving) return;
        captureDraft();
        try {
            EventInput input = new EventInput(text(0), text(1), text(2),
                    parseTime(text(4), model.originalStart), parseTime(text(5), model.originalEnd),
                    parseTime(text(6), model.originalDeadline), Integer.parseInt(text(7)), text(3),
                    (SourceType) sourceType.getSelectedItem(), text(8), text(9));
            model.save(eventId == 0 ? admin.create(input) : admin.update(eventId, model.expectedVersion, input));
        } catch (IllegalArgumentException exception) {
            status.setText("请检查时间格式 yyyy-MM-dd HH:mm 和名额整数");
        }
    }

    private void renderSaveState() {
        if (status == null) return;
        save.setEnabled(!model.saving && (eventId == 0 || model.expectedVersion > 0));
        for (EditText field : fields) field.setEnabled(!model.saving);
        sourceType.setEnabled(!model.saving);
        if (model.saving) status.setText("正在保存…");
        if (model.saveResult != null) {
            if (model.saveResult.success) {
                model.saveResult = null;
                navigator.back();
            } else status.setText(model.saveResult.code + " · " + model.saveResult.message);
        }
    }

    private String text(int index) { return fields[index].getText().toString().trim(); }
    private String format(long timestamp) { return dateFormat.format(new Date(timestamp)); }

    private long parseTime(String value, long original) {
        // Preserve milliseconds for untouched fields, so a content edit does not reschedule reminders.
        if (original > 0 && value.equals(format(original))) return original;
        ParsePosition position = new ParsePosition(0);
        Date parsed = dateFormat.parse(value, position);
        if (parsed == null || position.getIndex() != value.length() || !format(parsed.getTime()).equals(value)) {
            throw new IllegalArgumentException("Invalid date format");
        }
        return parsed.getTime();
    }

    private void captureDraft() {
        if (fields == null) return;
        Bundle draft = new Bundle();
        for (int i = 0; i < fields.length; i++) draft.putString(FIELD_KEYS[i], fields[i].getText().toString());
        draft.putInt("sourceType", sourceType.getSelectedItemPosition());
        model.draft = draft;
    }

    private void restoreDraft() {
        for (int i = 0; i < fields.length; i++) fields[i].setText(model.draft.getString(FIELD_KEYS[i], ""));
        sourceType.setSelection(model.draft.getInt("sourceType", SourceType.MANUAL.ordinal()));
    }

    @Override public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (eventId == 0 || model.expectedVersion > 0) captureDraft();
        state.putBundle("eventDraft", model.draft);
        state.putLong("eventVersion", model.expectedVersion);
        state.putLong("eventOriginalStart", model.originalStart);
        state.putLong("eventOriginalEnd", model.originalEnd);
        state.putLong("eventOriginalDeadline", model.originalDeadline);
    }

    @Override public void onDestroyView() {
        if (eventId == 0 || model.expectedVersion > 0) captureDraft();
        model.onChanged = null;
        if (loadCall != null) loadCall.cancelDelivery();
        if (activeDialog != null) activeDialog.dismiss();
        activeDialog = null;
        fields = null;
        sourceType = null;
        status = null;
        save = null;
        super.onDestroyView();
    }
}
