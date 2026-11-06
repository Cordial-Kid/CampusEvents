package com.example.campusevents.contract.session;

import com.example.campusevents.contract.common.AsyncCall;
import java.util.List;

public interface SessionApi {
    AsyncCall<List<UserSnapshot>> listDemoUsers();
    AsyncCall<SessionSnapshot> current();
    AsyncCall<SessionSnapshot> switchDemoUser(long userId);
}
