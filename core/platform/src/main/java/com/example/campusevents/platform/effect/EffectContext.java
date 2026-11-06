package com.example.campusevents.platform.effect;

import com.example.campusevents.platform.data.ReadWork;
import com.example.campusevents.platform.data.WriteWork;

public interface EffectContext {
    long requestedVersion();
    <T> T read(ReadWork<T> work);
    <T> T write(WriteWork<T> work);
}
