package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * The damage side of the "Bad Cough" symptom — a hidden effect applied for a short window that hurts the
 * player once per second (a violent coughing fit). Bypasses armor (magic damage): armor doesn't stop a
 * cough. The visible marker is the separate {@code BAD_COUGH} HUD effect; this one is icon/particle-free.
 */
public class CoughFitEffect extends MobEffect {
    private static final float DAMAGE_PER_HIT = 2.0F; // 1 heart
    private static final int   INTERVAL_TICKS = 20;   // once per second

    public CoughFitEffect() {
        super(MobEffectCategory.HARMFUL, 0x7A5230);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        entity.hurt(entity.damageSources().magic(), DAMAGE_PER_HIT);
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }
}
