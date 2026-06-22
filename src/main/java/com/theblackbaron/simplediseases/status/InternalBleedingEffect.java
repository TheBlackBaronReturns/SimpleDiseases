package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class InternalBleedingEffect extends SortedMobEffect {
    private static final float DAMAGE_PER_HIT = 0.75F;

    public InternalBleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A0020, EffectHudSort.SD_INTERNAL_BLEEDING);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (amplifier <= 0 || entity.level().isClientSide || entity.isInvulnerable()) return;
        int interval = damageInterval(amplifier);
        if (entity.level().getGameTime() % interval == 0L) {
            entity.hurt(entity.damageSources().magic(), DAMAGE_PER_HIT);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return amplifier > 0;
    }

    private static int damageInterval(int amplifier) {
        return switch (amplifier) {
            case 1 -> 200;
            case 2 -> 50;
            default -> 15;
        };
    }
}
