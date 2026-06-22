package com.theblackbaron.simplediseases.status;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MobEffect subclass that adds a chainable modifier() helper for building per-tier disease effects.
 * Tier variants share one icon per disease path (simplediseases:textures/mob_effect/&lt;diseasePath&gt;.png);
 * the client remaps sprite lookup via {@link #sharedIconId} and {@link com.theblackbaron.simplediseases.mixin.MobEffectTextureManagerMixin}.
 */
public class DiseaseMobEffect extends SortedMobEffect {

    /** MC WORLD-scale units added to the recovery threshold and perceived ambient warmth for each fever level. */
    public static final double FEVER_LIGHT  = 0.05;
    public static final double FEVER_MILD   = 0.10;
    public static final double FEVER_HIGH   = 0.15;
    public static final double FEVER_SEVERE = 0.20;

    /** MULTIPLY_TOTAL max-health penalty while Very High Fever is active (40°C). */
    public static final double FEVER_HIGH_MAX_HEALTH_FRAC   = 0.05;
    /** MULTIPLY_TOTAL max-health penalty while Hyperpyrexia is active (≥41°C). */
    public static final double FEVER_SEVERE_MAX_HEALTH_FRAC  = 0.15;
    /** MULTIPLY_TOTAL max-health penalty while septic shock is active. */
    public static final double SEPTIC_SHOCK_MAX_HEALTH_FRAC  = 0.25;

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
    private ResourceLocation sharedIconId;

    // LinkedHashMap preserves insertion order so JEED renders modifiers in the order modifier() is called.
    private final LinkedHashMap<Attribute, AttributeModifier> orderedModifiers = new LinkedHashMap<>();

    public DiseaseMobEffect(MobEffectCategory category, int color, int hudSortOrder) {
        super(category, color, hudSortOrder);
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

    /** MULTIPLY_TOTAL max-health penalty fraction for raw tier offsets; shock beats fever. */
    public static double maxHealthPenaltyFracFor(double feverOffset, double shockOffset) {
        if (shockOffset > 0.0) return SEPTIC_SHOCK_MAX_HEALTH_FRAC;
        if (feverOffset >= FEVER_SEVERE) return FEVER_SEVERE_MAX_HEALTH_FRAC;
        if (feverOffset >= FEVER_HIGH) return FEVER_HIGH_MAX_HEALTH_FRAC;
        return 0.0;
    }

    /** MULTIPLY_TOTAL max-health penalty fraction for this tier; shock beats fever. */
    public double maxHealthPenaltyFrac() {
        return maxHealthPenaltyFracFor(feverOffset, shockOffset);
    }

    /** Sets the shared atlas sprite id for all tiers of this disease path; chainable. */
    DiseaseMobEffect sharedIcon(ResourceLocation iconId) {
        this.sharedIconId = iconId;
        return this;
    }

    public ResourceLocation getSharedIconId() {
        return sharedIconId;
    }

    /**
     * Per-level malaise jump debuff (effective amount = this × (amplifier + 1)). Tuned so amp 3
     * still clears a 1-block step (vanilla jump ~1.25 blocks; factor must stay above ~0.89).
     */
    public static final double MALAISE_JUMP_DEBUFF = -0.025;

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
