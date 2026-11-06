package com.example.campusevents.platform.data;

public interface WriteWork<T> {
    T run(TxScope tx);
}
