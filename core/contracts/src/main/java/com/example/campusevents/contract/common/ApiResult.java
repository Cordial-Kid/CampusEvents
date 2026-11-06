package com.example.campusevents.contract.common;

import java.util.Objects;

/** A failed result never contains data. An OK result may contain null for a void action. */
public final class ApiResult<T> {
    public final boolean success;
    public final ResultCode code;
    public final String message;
    public final T data;

    private ApiResult(ResultCode code, String message, T data) {
        this.code = Objects.requireNonNull(code, "code");
        this.success = code == ResultCode.OK;
        this.message = Objects.requireNonNull(message, "message");
        this.data = data;
    }

    public static <T> ApiResult<T> ok(T data) {
        return new ApiResult<>(ResultCode.OK, "", data);
    }

    public static <T> ApiResult<T> failure(ResultCode code, String message) {
        if (code == ResultCode.OK) {
            throw new IllegalArgumentException("Failure code must not be OK");
        }
        return new ApiResult<>(code, message, null);
    }
}
