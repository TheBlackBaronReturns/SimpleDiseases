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

    /** MC WORLD-scale units added to the recovery threshold and perceived ambient warmth for each fever level. */
    public static final double FEVER_LIGHT  = 0.05;
    public static final double FEVER_MILD   = 0.10;
    public static final double FEVER_HIGH   = 0.15;
    public static final double FEVER_SEVERE = 0.20;

    /** MC WORLD-scale ambient penalty applied during septic shock (magnitude; applied as a negative WORLD offset). */
    public static final double SEPTIC_SHOCK_WORLD_OFFSET = 0.75;
    /** WORLD (MC scale) above default habitable max before shock cold-perception begins easing. */
    public static final double SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD = 1.8;
    /** WORLD units of shock penalty removed per MC WORLD unit above {@link #SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD}. */
    public static final double SEPTIC_SHOCK_HEAT_RELIEF_SCALE = 0.20;
    /** Maximum fraction of {@link #SEPTIC_SHOCK_WORLD_OFFSET} that environmental heat can relieve. */
    public static final double SEPTIC_SHOCK_MAX_HEAT_RELIEF_FRAC = 0.55;

    /** @deprecated Use {@link #SEPTIC_SHOCK_WORLD_OFFSET}. */
    @Deprecated
    public static final double SEPTIC_SHOCK_BASE_OFFSET = SEPTIC_SHOCK_WORLD_OFFSET;

    /** @deprecated Use {@link #SEPTIC_SHOCK_WORLD_OFFSET}. */
    @Deprecated
    public static final double SEPTIC_SHOCK_STRENGTH = SEPTIC_SHOCK_WORLD_OFFSET;

    private double feverOffset = 0.0;
    private double shockOffset = 0.0;

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

    /** Sets the MC WORLD-scale fever offset (recovery threshold penalty + perceived warmth); chainable. */
    public DiseaseMobEffect fever(double offset) {
        this.feverOffset = offset;
        return this;
    }

    public double getFeverOffset() {
        return feverOffset;
    }

    /** Sets the septic shock WORLD penalty magnitude; chainable. Applied via {@link com.theblackbaron.simplediseases.compat.SepticShockTempModifier}. */
    public DiseaseMobEffect shock(double offset) {
        this.shockOffset = offset;
        return this;
    }

    public double getShockOffset() {
        return shockOffset;
    }

    /** Maps disease fever/shock tier to malaise MobEffect amplifier (display levels 1–4 → amp 0–3). */
    public static int malaiseAmplifierFrom(DiseaseMobEffect effect) {
        if (effect.getShockOffset() > 0) return 3;
        double f = effect.getFeverOffset();
        if (f >= FEVER_SEVERE) return 3;
        if (f >= FEVER_HIGH)   return 2;
        if (f >= FEVER_MILD)   return 1;
        return 0;
    }
}
