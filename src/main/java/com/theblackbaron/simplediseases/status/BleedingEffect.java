package com.theblackbaron.simplediseases.status;

import com.theblackbaron.simplediseases.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class BleedingEffect extends MobEffect {
    private static final float DAMAGE_PER_HIT = 0.5F;

    public BleedingEffect() {
        super(MobEffectCategory.HARMFUL, 0x8C1000);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (amplifier <= 0 || entity.level().isClientSide || entity.isInvulnerable()) return;
        int interval = damageInterval(amplifier);
        if (entity.level().getGameTime() % interval == 0L) {
            if (entity instanceof ServerPlayer player) {
                NetworkHandler.sendBleedingSplatter(player, 3);
            }
            entity.hurt(entity.damageSources().magic(), DAMAGE_PER_HIT);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return amplifier > 0;
    }

    private static int damageInterval(int amplifier) {
        return switch (amplifier) {
            case 1 -> 300;
            case 2 -> 75;
            default -> 20;
        };
    }
}
