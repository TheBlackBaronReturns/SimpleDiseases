package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffectCategory;

public class ImmuneEffect extends SortedMobEffect {
    public ImmuneEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x4CAF50, EffectHudSort.IMMUNE);
    }
}
