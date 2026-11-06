package com.example.campusevents.contract.common;

/** Cancels delivery to the caller. An already queued write may still commit. */
public interface CallHandle {
    void cancelDelivery();
}
