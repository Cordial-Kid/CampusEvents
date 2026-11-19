package com.example.campusevents.engagement;

/** Android boundary kept injectable so reminder rules can be tested without a device. */
public interface EngagementSystem {
    boolean canDeliverNotifications();
    boolean canScheduleExactAlarms();
    void schedule(ReminderSchedule reminder);
    void cancel(int scheduleId);
    void post(ReminderNotification notification);
}
