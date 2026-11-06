package com.example.campusevents.contract.common;

import java.util.Objects;

/** Separates unavailable development modules from real empty or zero-valued results. */
public final class ModuleValue<T> {
    public enum State { READY, NOT_READY }

    public final State state;
    public final T data;

    private ModuleValue(State state, T data) {
        this.state = state;
        this.data = data;
    }

    public static <T> ModuleValue<T> ready(T data) {
        return new ModuleValue<>(State.READY, Objects.requireNonNull(data, "data"));
    }

    public static <T> ModuleValue<T> notReady() {
        return new ModuleValue<>(State.NOT_READY, null);
    }

    public boolean isReady() {
        return state == State.READY;
    }
}
