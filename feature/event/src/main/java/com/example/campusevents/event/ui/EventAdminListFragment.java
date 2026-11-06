package com.example.campusevents.event.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.campusevents.contract.common.CallHandle;
import com.example.campusevents.contract.event.EventAdminApi;
import com.example.campusevents.contract.event.EventFilter;
import com.example.campusevents.contract.event.EventReadApi;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.event.R;
import com.example.campusevents.platform.ui.Navigator;
import com.example.campusevents.platform.ui.Route;
import com.example.campusevents.platform.ui.ScreenKey;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public final class EventAdminListFragment extends Fragment {
    private final EventReadApi reads;
    private final EventAdminApi admin;
    private final Navigator navigator;
    private final List<CallHandle> calls = new ArrayList<>();
    private EventAdminViewModel model;
    private LinearLayout rows;
    private TextView status;
    private int requestSequence;
    private AlertDialog activeDialog;

    public EventAdminListFragment(EventReadApi reads, EventAdminApi admin, Navigator navigator) {
        this.reads = reads;
        this.admin = admin;
        this.navigator = navigator;
    }

    @Override public View onCreateView(LayoutInflater inflater, ViewGroup parent, Bundle savedState) {
        View view = inflater.inflate(R.layout.event_admin_list, parent, false);
        model = new ViewModelProvider(this).get(EventAdminViewModel.class);
        rows = view.findViewById(R.id.event_rows);
        status = view.findViewById(R.id.event_list_status);
        view.findViewById(R.id.event_create).setOnClickListener(v -> navigator.open(Route.to(ScreenKey.ADMIN_CREATE)));
        view.findViewById(R.id.event_refresh).setOnClickListener(v -> refresh());
        render();
        return view;
    }

    @Override public void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        int sequence = ++requestSequence;
        status.setText("正在读取活动…");
        calls.add(reads.list(new EventFilter()).enqueue(result -> {
            if (rows == null || sequence != requestSequence) return;
            if (!result.success) {
                status.setText(result.code + " · " + result.message);
                return;
            }
            model.events = result.data;
            status.setText(model.events.isEmpty() ? "暂无活动，点击新增活动" : "共 " + model.events.size() + " 场活动");
            render();
        }));
    }

    private void render() {
        rows.removeAllViews();
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);
        format.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        for (EventRecord event : model.events) {
            View row = getLayoutInflater().inflate(R.layout.event_admin_row, rows, false);
            ((TextView) row.findViewById(R.id.event_row_title)).setText(event.title);
            ((TextView) row.findViewById(R.id.event_row_description)).setText(
                    format.format(new Date(event.startAt)) + " · " + event.location
                    + "\n名额 " + event.capacity + " · " + event.status + " · 版本 " + event.version);
            row.findViewById(R.id.event_row_detail).setOnClickListener(v -> navigator.open(Route.event(ScreenKey.EVENT_DETAIL, event.id)));
            Button edit = row.findViewById(R.id.event_row_edit);
            Button cancel = row.findViewById(R.id.event_row_cancel);
            boolean editable = event.status == EventStatus.ACTIVE && event.startAt > System.currentTimeMillis();
            edit.setEnabled(editable);
            cancel.setEnabled(editable);
            edit.setOnClickListener(v -> navigator.open(Route.event(ScreenKey.ADMIN_EDIT, event.id)));
            cancel.setOnClickListener(v -> confirmCancel(event));
            rows.addView(row);
        }
    }

    private void confirmCancel(EventRecord event) {
        activeDialog = new AlertDialog.Builder(requireContext()).setTitle("取消活动")
                .setMessage("确认取消“" + event.title + "”？有效报名和待提醒将在同一事务中关闭。")
                .setNegativeButton("返回", null)
                .setPositiveButton("确认取消", (dialog, which) -> {
                    status.setText("正在取消…");
                    calls.add(admin.cancel(event.id, event.version).enqueue(result -> {
                        if (status == null) return;
                        if (result.success) refresh();
                        else status.setText(result.code + " · " + result.message);
                    }));
                }).show();
    }

    @Override public void onDestroyView() {
        requestSequence++;
        for (CallHandle call : calls) call.cancelDelivery();
        calls.clear();
        if (activeDialog != null) activeDialog.dismiss();
        activeDialog = null;
        rows = null;
        status = null;
        super.onDestroyView();
    }
}
