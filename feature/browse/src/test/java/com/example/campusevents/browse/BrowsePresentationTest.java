package com.example.campusevents.browse;

import static org.junit.Assert.assertEquals;

import com.example.campusevents.contract.browse.EventView;
import com.example.campusevents.contract.common.ModuleValue;
import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.registration.CancelReason;
import com.example.campusevents.contract.registration.RegistrationStatus;
import com.example.campusevents.contract.registration.RegistrationSummary;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public final class BrowsePresentationTest {
    @Test public void groupsByBeijingDateAcrossUtcMidnightBoundary() {
        assertEquals("2026-11-12", BrowsePresentation.format(
                Instant.parse("2026-11-11T16:00:00Z").toEpochMilli(), "yyyy-MM-dd"));
        assertEquals("2026-11-11", BrowsePresentation.format(
                Instant.parse("2026-11-11T15:59:59Z").toEpochMilli(), "yyyy-MM-dd"));
    }

    @Test public void derivesStatusAtExactBoundariesAndPrioritizesCancellation() {
        ModuleValue<RegistrationSummary> pending = ModuleValue.notReady();
        assertEquals(BrowsePresentation.Status.UPCOMING,
                BrowsePresentation.status(event(1, "TECH", EventStatus.ACTIVE, 9, pending)));
        assertEquals(BrowsePresentation.Status.REGISTRATION_CLOSED,
                BrowsePresentation.status(event(1, "TECH", EventStatus.ACTIVE, 10, pending)));
        assertEquals(BrowsePresentation.Status.STARTED,
                BrowsePresentation.status(event(1, "TECH", EventStatus.ACTIVE, 20, pending)));
        assertEquals(BrowsePresentation.Status.ENDED,
                BrowsePresentation.status(event(1, "TECH", EventStatus.ACTIVE, 30, pending)));
        assertEquals(BrowsePresentation.Status.CANCELLED,
                BrowsePresentation.status(event(1, "TECH", EventStatus.CANCELLED, 30, pending)));
    }

    @Test public void onlyRecommendsFirstThreeMatchingActivitiesConfirmedByRegistration() {
        ModuleValue<RegistrationSummary> available = registration(true);
        EventView first = event(4, "TECH", EventStatus.ACTIVE, 0, available);
        EventView second = event(5, "TECH", EventStatus.ACTIVE, 0, available);
        EventView third = event(6, "TECH", EventStatus.ACTIVE, 0, available);
        assertEquals(Arrays.asList(first, second, third), BrowsePresentation.recommend(Arrays.asList(
                event(1, "SPORT", EventStatus.ACTIVE, 0, available),
                event(2, "TECH", EventStatus.ACTIVE, 0, ModuleValue.notReady()),
                event(3, "TECH", EventStatus.ACTIVE, 0, registration(false)),
                first, second, third,
                event(7, "TECH", EventStatus.ACTIVE, 0, available)),
                Collections.singleton("TECH")));
        assertEquals(Collections.emptyList(),
                BrowsePresentation.recommend(Collections.singletonList(first), Collections.emptySet()));
    }

    private static ModuleValue<RegistrationSummary> registration(boolean available) {
        return ModuleValue.ready(new RegistrationSummary(0, 10, RegistrationStatus.NONE,
                CancelReason.NONE, available, available ? ResultCode.OK : ResultCode.EVENT_FULL, false));
    }

    private static EventView event(long id, String category, EventStatus status, long now,
                                   ModuleValue<RegistrationSummary> registration) {
        EventRecord record = new EventRecord(id, "标题", "介绍", "地点", 20, 30, 10, 10,
                category, SourceType.MANUAL, "课程作业演示数据", "", status, 1, 0, 0, 1);
        return new EventView(record, now, registration, ModuleValue.notReady());
    }
}
