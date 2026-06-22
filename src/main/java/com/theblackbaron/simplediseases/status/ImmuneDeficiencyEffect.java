package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffectCategory;

public class ImmuneDeficiencyEffect extends SortedMobEffect {
    public ImmuneDeficiencyEffect() {
        super(MobEffectCategory.HARMFUL, 0x8B0000, EffectHudSort.IMMUNE_DEFICIENCY);
    }
}
