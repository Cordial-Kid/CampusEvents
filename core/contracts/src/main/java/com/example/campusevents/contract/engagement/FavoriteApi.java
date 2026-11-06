package com.example.campusevents.contract.engagement;

import com.example.campusevents.contract.common.AsyncCall;
import java.util.List;

public interface FavoriteApi {
    /** Idempotently requests a target state, never toggles blindly. */
    AsyncCall<Boolean> setFavorite(long eventId, boolean desiredState);
    AsyncCall<List<FavoriteItem>> listMine();
}
