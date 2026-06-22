package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffectCategory;

public class BloodLossEffect extends SortedMobEffect {
    public BloodLossEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B0000, EffectHudSort.SD_BLOOD_LOSS);
    }
}
