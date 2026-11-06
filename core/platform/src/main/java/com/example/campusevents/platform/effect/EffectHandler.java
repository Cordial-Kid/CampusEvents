package com.example.campusevents.platform.effect;

public interface EffectHandler {
    EffectKey key();
    EffectOutcome reconcile(EffectContext context);
}
