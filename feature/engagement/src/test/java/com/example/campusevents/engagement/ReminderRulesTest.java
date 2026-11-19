package com.example.campusevents.engagement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.campusevents.contract.common.ResultCode;
import com.example.campusevents.contract.event.EventRecord;
import com.example.campusevents.contract.event.EventStatus;
import com.example.campusevents.contract.event.SourceType;
import com.example.campusevents.contract.session.Role;
import com.example.campusevents.contract.session.UserSnapshot;
import org.junit.Test;

public final class ReminderRulesTest {
    private static final long START = 10_000_000L;

    @Test public void computesTriggerWithLongArithmetic() {
        assertEquals(8_200_000L, ReminderRules.triggerAt(START, 30));
        assertEquals(9_940_000L, ReminderRules.triggerAt(START, 1));
    }

    @Test public void rejectsInvalidLeadBeforeChangingIntent() {
        assertFalse(ReminderRules.hasValidLead(0));
        assertFalse(ReminderRules.hasValidLead(-1));
        assertTrue(ReminderRules.hasValidLead(1));
        assertEquals(ResultCode.INVALID_INPUT, ReminderRules.setRejection(
                student(), activeEvent(), 0, START - 1, 1_000L));
    }

    @Test public void appliesIdentityAndLifecycleRejectionOrder() {
        EventRecord active = activeEvent();
        assertEquals(ResultCode.NOT_LOGGED_IN,
                ReminderRules.setRejection(null, active, 30, START - 60_000, 1_000L));
        assertEquals(ResultCode.FORBIDDEN, ReminderRules.setRejection(
                new UserSnapshot(1, "管理员", Role.ADMIN), active, 30, START - 60_000, 1_000L));
        assertEquals(ResultCode.EVENT_CANCELLED, ReminderRules.setRejection(
                student(), cancelledEvent(), 30, START - 60_000, 1_000L));
        assertEquals(ResultCode.EVENT_STARTED, ReminderRules.setRejection(
                student(), active, 30, START - 60_000, START));
        assertEquals(ResultCode.REMINDER_TIME_PASSED, ReminderRules.setRejection(
                student(), active, 30, START - 30_000, START - 30_000));
        assertEquals(ResultCode.OK, ReminderRules.setRejection(
                student(), active, 30, START - 30_000, START - 30_001));
    }

    @Test public void onlyFuturePendingActiveReminderIsSchedulable() {
        EventRecord active = activeEvent();
        assertTrue(ReminderRules.isSchedulable(active, START - 60_000, START - 60_001));
        assertFalse(ReminderRules.isSchedulable(active, START - 60_000, START));
        assertFalse(ReminderRules.isSchedulable(cancelledEvent(), START - 60_000, START - 1));
        assertFalse(ReminderRules.isSchedulable(active, START + 1, START - 1));
    }

    private static UserSnapshot student() {
        return new UserSnapshot(2, "学生甲", Role.STUDENT);
    }

    private static EventRecord activeEvent() {
        return event(EventStatus.ACTIVE);
    }

    private static EventRecord cancelledEvent() {
        return event(EventStatus.CANCELLED);
    }

    private static EventRecord event(EventStatus status) {
        return new EventRecord(5, "测试活动", "说明", "教室", START, START + 60_000,
                START - 1, 20, "TECH", SourceType.MANUAL, "测试", "", status,
                1, 0, 0, 1);
    }
}
