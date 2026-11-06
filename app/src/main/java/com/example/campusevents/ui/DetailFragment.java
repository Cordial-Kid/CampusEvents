package com.example.campusevents.ui;
import android.os.Bundle;
import android.view.View;
import androidx.fragment.app.Fragment;
import com.example.campusevents.R;
import com.example.campusevents.platform.ui.*;
public final class DetailFragment extends Fragment {
    private final UiRegistry ui;
    public DetailFragment(UiRegistry ui) { super(R.layout.shell_detail); this.ui=ui; }
    @Override public void onViewCreated(View view, Bundle state) {
        long eventId = requireArguments().getLong(RouteArgs.EVENT_ID);
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        if (getChildFragmentManager().findFragmentById(R.id.shell_detail_content_slot) != null) return;
        getChildFragmentManager().beginTransaction()
            .replace(R.id.shell_detail_content_slot,ui.createPanel(DetailSlot.CONTENT,eventId))
            .replace(R.id.shell_detail_registration_slot,ui.createPanel(DetailSlot.REGISTRATION,eventId))
            .replace(R.id.shell_detail_engagement_slot,ui.createPanel(DetailSlot.ENGAGEMENT,eventId)).commit();
    }
}
