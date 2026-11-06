package com.example.campusevents.platform;

import android.content.Context;
import com.example.campusevents.contract.session.SessionApi;
import com.example.campusevents.platform.data.DataAccess;
import com.example.campusevents.platform.data.SerialDataAccess;
import com.example.campusevents.platform.db.CampusDatabase;
import com.example.campusevents.platform.db.SchemaContributor;
import com.example.campusevents.platform.effect.EffectHandler;
import com.example.campusevents.platform.invalidation.DataInvalidation;
import java.util.List;

/** Application-owned infrastructure. Pass individual capabilities to feature factories. */
public final class Platform implements AutoCloseable {
    private final DataInvalidation invalidation = new DataInvalidation();
    private final SerialDataAccess data;

    public Platform(Context context, List<SchemaContributor> schemas) {
        data = new SerialDataAccess(new CampusDatabase(context, schemas), invalidation);
    }
    public DataAccess dataAccess() { return data; }
    public SessionApi sessionApi() { return data; }
    public DataInvalidation invalidation() { return invalidation; }
    public void registerEffectHandler(EffectHandler handler) { data.registerEffectHandler(handler); }
    public void start() { data.start(); }
    public void retryEffects() { data.retryEffects(); }
    @Override public void close() { data.close(); }
}
