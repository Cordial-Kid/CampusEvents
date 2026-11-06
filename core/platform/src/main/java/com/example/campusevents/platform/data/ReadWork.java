package com.example.campusevents.platform.data;

public interface ReadWork<T> {
    T run(ReadScope scope);
}
