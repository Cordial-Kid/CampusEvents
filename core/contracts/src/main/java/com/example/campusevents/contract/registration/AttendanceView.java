package com.example.campusevents.contract.registration;
public final class AttendanceView {
    public final long userId;
    public final Long checkedInAt;
    public final Integer rating;
    public final String feedback;
    public final boolean canCheckIn, canFeedback;
    public final String message;
    public AttendanceView(long userId, Long checkedInAt, Integer rating, String feedback,
                          boolean canCheckIn, boolean canFeedback, String message) {
        this.userId=userId; this.checkedInAt=checkedInAt; this.rating=rating; this.feedback=feedback;
        this.canCheckIn=canCheckIn; this.canFeedback=canFeedback; this.message=message;
    }
}
