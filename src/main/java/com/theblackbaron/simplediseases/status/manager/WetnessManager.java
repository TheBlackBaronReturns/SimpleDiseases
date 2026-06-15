package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.compat.ColdSweatCompat;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

public final class WetnessManager {

    public void tick(ServerPlayer player, PlayerDiseaseState data) {
        if (player.isUnderWater()) {
            data.addWetProgress(0.03);
        } else if (player.isInWater()) {
            if (data.getWetProgress() < 0.7) data.addWetProgress(0.01);
        } else if (player.level().isRainingAt(player.blockPosition())) {
            data.addWetProgress(0.0004);
        } else if (player.isOnFire()) {
            data.addWetProgress(-0.005);
            if (data.getWetProgress() > 0.0) player.setRemainingFireTicks(0);
        } else if (data.getWetProgress() > 0.0) {
            // Already-dry players skip the drying calc entirely — getDryRate is a Cold Sweat WORLD-trait
            // read, and -rate would clamp to 0 here anyway. Avoids a per-tick CS temperature evaluation
            // for the (common) dry player.
            data.addWetProgress(-ColdSweatCompat.getDryRate(player));
        }

        double wet = data.getWetProgress();
        if (wet >= 0.10) {
            int newAmp = wet >= 0.72 ? 2 : wet >= 0.40 ? 1 : 0;
            MobEffectInstance current = player.getEffect(DiseaseEffects.DAMP.get());
            if (current == null) {
                player.addEffect(new MobEffectInstance(DiseaseEffects.DAMP.get(), -1, newAmp, false, false, true));
            } else if (current.getAmplifier() != newAmp) {
                player.removeEffect(DiseaseEffects.DAMP.get());
                player.addEffect(new MobEffectInstance(DiseaseEffects.DAMP.get(), -1, newAmp, false, false, true));
            }
        } else if (player.hasEffect(DiseaseEffects.DAMP.get())) {
            player.removeEffect(DiseaseEffects.DAMP.get());
        }
    }
}
