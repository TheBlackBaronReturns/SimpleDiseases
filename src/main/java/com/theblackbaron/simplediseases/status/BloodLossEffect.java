package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class BloodLossEffect extends MobEffect {
    public BloodLossEffect() {
        super(MobEffectCategory.HARMFUL, 0x6B0000);
    }
}
