package com.example.campusevents.contract.common;

@FunctionalInterface
public interface ResultCallback<T> {
    void onResult(ApiResult<T> result);
}
