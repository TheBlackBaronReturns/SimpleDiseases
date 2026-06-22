package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * MobEffect with an explicit Forge GUI sort order via {@link #getSortOrder}.
 */
public class SortedMobEffect extends MobEffect {

    private final int hudSortOrder;

    public SortedMobEffect(MobEffectCategory category, int color, int hudSortOrder) {
        super(category, color);
        this.hudSortOrder = hudSortOrder;
    }

    @Override
    public int getSortOrder(MobEffectInstance instance) {
        return hudSortOrder;
    }
}
