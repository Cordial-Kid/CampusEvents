package com.example.campusevents.browse;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.*;
import com.example.campusevents.contract.browse.*;
import com.example.campusevents.contract.event.*;
import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.platform.invalidation.*;
import com.example.campusevents.platform.ui.*;
import java.text.SimpleDateFormat;
import java.util.*;

public final class BrowseFragment extends Fragment {
    private final EventBrowseApi api;
    private final DataInvalidation invalidation;
    private final Navigator navigator;
    private BrowseViewModel model;
    private DataInvalidation.Subscription subscription;
    public BrowseFragment(EventBrowseApi api, DataInvalidation invalidation, Navigator navigator) {
        super(R.layout.browse_content); this.api=api; this.invalidation=invalidation; this.navigator=navigator;
    }
    @Override public void onViewCreated(View view, Bundle state) {
        long eventId = requireArguments().getLong(RouteArgs.EVENT_ID, 0);
        model = new ViewModelProvider(this, new ViewModelProvider.Factory() {
            @Override public <T extends ViewModel> T create(Class<T> type) { return type.cast(new BrowseViewModel(api)); }
        }).get(BrowseViewModel.class);
        TextView status = view.findViewById(R.id.browse_status);
        LinearLayout rows = view.findViewById(R.id.browse_rows);
        view.findViewById(R.id.browse_refresh).setOnClickListener(v -> model.refresh(eventId));
        model.state.observe(getViewLifecycleOwner(), result -> {
            rows.removeAllViews();
            if (!result.success) { status.setText(result.message); return; }
            status.setText(result.data.isEmpty() ? "暂无活动" : getString(R.string.browse_baseline));
            for (EventView item : result.data) {
                EventRecord e = item.event;
                TextView row = new TextView(requireContext());
                row.setTextSize(17); row.setPadding(0, 12, 0, 24);
                String reg = item.registration.state == ModuleValue.State.READY
                    ? "已报名 " + item.registration.data.activeCount + " 人 · 剩余 " + item.registration.data.remainingCapacity
                    : "报名状态待 C 接入";
                String text = e.title + "\n" + format(e.startAt) + " 至 " + format(e.endAt)
                    + "\n" + e.location + " · " + (e.status == EventStatus.CANCELLED ? "已取消" : "正常") + "\n" + reg;
                if (eventId > 0) text += "\n报名截止 " + format(e.registrationDeadline)
                    + "\n" + e.description + "\n来源 " + e.sourceName + "（" + e.sourceType + "）\n" + e.sourceUrl;
                row.setText(text);
                if (eventId == 0) row.setOnClickListener(v -> navigator.open(Route.event(ScreenKey.EVENT_DETAIL, e.id)));
                rows.addView(row);
            }
        });
        subscription = invalidation.subscribe(change -> model.refresh(eventId));
        model.refresh(eventId);
    }
    private String format(long time) {
        SimpleDateFormat f = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai")); return f.format(new Date(time));
    }
    @Override public void onResume() { super.onResume(); if (model != null) model.refresh(requireArguments().getLong(RouteArgs.EVENT_ID,0)); }
    @Override public void onDestroyView() {
        if (subscription != null) subscription.unsubscribe();
        if (model != null) model.cancel(); super.onDestroyView();
    }
}
