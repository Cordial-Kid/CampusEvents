package com.example.campusevents.contract.registration;

import com.example.campusevents.contract.common.AsyncCall;
import java.util.List;

public interface RegistrationApi {
    AsyncCall<RegistrationSummary> getSummary(long eventId);
    AsyncCall<RegistrationSummary> register(long eventId);
    AsyncCall<RegistrationSummary> cancelRegistration(long eventId);
    AsyncCall<List<RegistrationItem>> listMine();
}
