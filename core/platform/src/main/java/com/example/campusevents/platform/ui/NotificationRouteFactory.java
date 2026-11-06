package com.example.campusevents.platform.ui;

import android.app.PendingIntent;

public interface NotificationRouteFactory {
    PendingIntent eventDetail(long eventId, long recipientUserId);
}
