package com.theblackbaron.simplediseases.status;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hypotension episode marker; every 30 s while active applies 10 s of hidden blindness + Slowness IV.
 */
public class HypotensionEffect extends MobEffect {
    public static final int PULSE_INTERVAL_TICKS = 20 * 30;
    public static final int IMPACT_TICKS         = 200;

    public HypotensionEffect() {
        super(MobEffectCategory.NEUTRAL, 0x7090B0);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity instanceof ServerPlayer player)) return;
        if (entity.level().isClientSide()) return;
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, IMPACT_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, IMPACT_TICKS, 3, false, false, false));
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % PULSE_INTERVAL_TICKS == 0;
    }
}
