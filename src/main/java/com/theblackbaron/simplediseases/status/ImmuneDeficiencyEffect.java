package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

public class ImmuneDeficiencyEffect extends MobEffect {
    public ImmuneDeficiencyEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000);
    }
}
