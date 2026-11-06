package com.example.campusevents.contract.common;

/** A lazy, single-use request. The callback is delivered on the Android main thread. */
public interface AsyncCall<T> {
    CallHandle enqueue(ResultCallback<T> callback);
}
