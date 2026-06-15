package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

public class MalaiseEffect extends MobEffect {
    public MalaiseEffect() {
        super(MobEffectCategory.NEUTRAL, 0x6E6B7A);
        addAttributeModifier(Attributes.ATTACK_SPEED,
            "30000001-3000-3000-3000-300000000001",
            -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }
}
