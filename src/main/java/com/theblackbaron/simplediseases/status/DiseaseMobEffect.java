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
    public static final double FEVER_LIGHT  =  5.0;
    public static final double FEVER_MILD   = 10.0;
    public static final double FEVER_HIGH   = 15.0;
    public static final double FEVER_SEVERE = 20.0;

    /** CS BASE-trait hypothermia floor for septic shock (BODY = CORE + BASE; not a setpoint). */
    public static final double SEPTIC_SHOCK_BASE_OFFSET = 38.0;
    /** Extra cooling multiplier on negative RATE during shock (vasodilation / heat loss). */
    public static final double SEPTIC_SHOCK_COOL_MULT = 1.25;
    /** Pre-armor warming multiplier when CORE is below comfort (superseded post-armor by REWARM_MULT). */
    public static final double SEPTIC_SHOCK_WARM_BOOST = 2.0;
    /** Post-armor RATE multiplier applied after insulation during hypothermic rewarming. */
    public static final double SEPTIC_SHOCK_REWARM_MULT = 2.75;
    /** WORLD (MC scale) above default habitable max before BASE hypothermia floor begins easing. */
    public static final double SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD = 1.8;
    /** CS units of BASE penalty removed per MC WORLD unit above {@link #SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD}. */
    public static final double SEPTIC_SHOCK_HEAT_RELIEF_SCALE = 6.0;
    /** Maximum fraction of {@link #SEPTIC_SHOCK_BASE_OFFSET} that environmental heat can relieve. */
    public static final double SEPTIC_SHOCK_MAX_HEAT_RELIEF_FRAC = 0.55;

    /** @deprecated Use {@link #SEPTIC_SHOCK_BASE_OFFSET}. */
    @Deprecated
    public static final double SEPTIC_SHOCK_STRENGTH = SEPTIC_SHOCK_BASE_OFFSET;

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

    /** Sets the CS BODY temperature offset required on top of the base threshold to recover; chainable. */
    public DiseaseMobEffect fever(double offset) {
        this.feverOffset = offset;
        return this;
    }

    public double getFeverOffset() {
        return feverOffset;
    }

    /** Sets the septic shock BASE penalty in CS body units; chainable. Applied via {@link com.theblackbaron.simplediseases.compat.SepticShockTempModifier}. */
    public DiseaseMobEffect shock(double offset) {
        this.shockOffset = offset;
        return this;
    }

    public double getShockOffset() {
        return shockOffset;
    }
}
