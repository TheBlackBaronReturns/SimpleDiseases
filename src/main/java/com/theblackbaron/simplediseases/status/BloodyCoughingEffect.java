package com.theblackbaron.simplediseases.status;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Visible Bloody Coughing marker for a full episode; magic damage ticks only during an NBT-stored
 * damage window started when the symptom episode fires.
 */
public class BloodyCoughingEffect extends MobEffect {
    private static final String KEY_DAMAGE_UNTIL = "bloody_cough_damage_until";
    private static final float  DAMAGE_PER_HIT   = 2.0F;
    private static final int    INTERVAL_TICKS   = 20;

    public BloodyCoughingEffect() {
        super(MobEffectCategory.NEUTRAL, 0x8B3A3A);
    }

    public static void beginDamageWindow(ServerPlayer player, long gameTimeUntil) {
        nbt(player).putLong(KEY_DAMAGE_UNTIL, gameTimeUntil);
    }

    public static void clearDamageWindow(ServerPlayer player) {
        nbt(player).remove(KEY_DAMAGE_UNTIL);
    }

    private static long damageUntil(LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player)) return 0L;
        return nbt(player).getLong(KEY_DAMAGE_UNTIL);
    }

    private static CompoundTag nbt(ServerPlayer player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(SimpleDiseases.MOD_ID)) {
            root.put(SimpleDiseases.MOD_ID, new CompoundTag());
        }
        return root.getCompound(SimpleDiseases.MOD_ID);
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (!(entity.level() instanceof net.minecraft.server.level.ServerLevel)) return;
        long until = damageUntil(entity);
        if (until <= 0L) return;
        if (entity.level().getGameTime() < until) {
            entity.hurt(entity.damageSources().magic(), DAMAGE_PER_HIT);
        }
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        return duration % INTERVAL_TICKS == 0;
    }
}
