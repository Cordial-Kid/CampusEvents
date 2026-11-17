package com.example.campusevents.browse;

import com.example.campusevents.contract.browse.EventView;
import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

final class BrowsePresentation {
    enum Status { UPCOMING, REGISTRATION_CLOSED, STARTED, ENDED, CANCELLED }

    private static final TimeZone BEIJING = TimeZone.getTimeZone("Asia/Shanghai");

    private BrowsePresentation() {}

    static String format(long timestamp, String pattern) {
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, Locale.CHINA);
        formatter.setTimeZone(BEIJING);
        return formatter.format(new Date(timestamp));
    }

    static Status status(EventView item) {
        EventRecord event = item.event;
        long now = item.evaluatedAt;
        if (event.status == EventStatus.CANCELLED) return Status.CANCELLED;
        if (now >= event.endAt) return Status.ENDED;
        if (now >= event.startAt) return Status.STARTED;
        if (now >= event.registrationDeadline) return Status.REGISTRATION_CLOSED;
        return Status.UPCOMING;
    }

    static List<EventView> recommend(List<EventView> timeline, Set<String> interests) {
        List<EventView> recommendations = new ArrayList<>();
        for (EventView item : timeline) {
            if (interests.contains(item.event.category)
                    && item.registration.state == ModuleValue.State.READY
                    && item.registration.data.canRegister) {
                recommendations.add(item);
                if (recommendations.size() == 3) break;
            }
        }
        return recommendations;
    }
}
