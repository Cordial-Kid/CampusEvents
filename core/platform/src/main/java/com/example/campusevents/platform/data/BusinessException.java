package com.example.campusevents.platform.data;

import com.example.campusevents.contract.common.ResultCode;
import java.util.Objects;

public final class BusinessException extends RuntimeException {
    public final ResultCode code;
    public BusinessException(ResultCode code, String message) {
        super(Objects.requireNonNull(message, "message"));
        if (code == ResultCode.OK) throw new IllegalArgumentException("Failure code required");
        this.code = Objects.requireNonNull(code, "code");
    }
}
