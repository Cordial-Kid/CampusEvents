package com.example.campusevents.platform.data;

import com.example.campusevents.contract.common.AsyncCall;

public interface DataAccess {
    <T> AsyncCall<T> read(ReadWork<T> work);
    <T> AsyncCall<T> write(WriteWork<T> work);
    default <T> AsyncCall<T> query(ReadWork<T> work) { return read(work); }
    default <T> AsyncCall<T> command(WriteWork<T> work) { return write(work); }
}
