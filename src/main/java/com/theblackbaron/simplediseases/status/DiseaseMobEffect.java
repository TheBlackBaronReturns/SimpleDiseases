package com.theblackbaron.simplediseases.status;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MobEffect subclass that adds a chainable modifier() helper for building per-tier disease effects.
 * Icon textures are provided per-tier as standard atlas PNGs (simplediseases:textures/mob_effect/<regName>.png).
 */
public class DiseaseMobEffect extends MobEffect {

    /** CS BODY units added to the recovery threshold for each fever level (used by DiseaseEffects). */
    public static final double FEVER_LIGHT  = 10.0;
    public static final double FEVER_MILD   = 20.0;
    public static final double FEVER_HIGH   = 35.0;
    public static final double FEVER_SEVERE = 50.0;

    private double feverOffset = 0.0;

    // LinkedHashMap preserves insertion order so JEED renders modifiers in the order modifier() is called.
    private final LinkedHashMap<Attribute, AttributeModifier> orderedModifiers = new LinkedHashMap<>();

    public DiseaseMobEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public Map<Attribute, AttributeModifier> getAttributeModifiers() {
        return orderedModifiers;
    }

    /** Adds an attribute modifier; chainable. The UUID must be unique per (variant, attribute). */
    public DiseaseMobEffect modifier(Attribute attribute, UUID uuid, double amount, AttributeModifier.Operation operation) {
        addAttributeModifier(attribute, uuid.toString(), amount, operation);
        orderedModifiers.put(attribute, new AttributeModifier(uuid, this::getDescriptionId, amount, operation));
        return this;
    }

    /** Sets the CS BODY temperature offset required on top of the base threshold to recover; chainable. */
    public DiseaseMobEffect fever(double offset) {
        this.feverOffset = offset;
        return this;
    }

    public double getFeverOffset() {
        return feverOffset;
    }
}
