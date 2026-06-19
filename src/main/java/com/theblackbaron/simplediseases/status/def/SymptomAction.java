package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * A side effect a symptom triggers when its episode fires, beyond applying the symptom's MobEffect.
 * Kept as a small closed vocabulary (not a code lambda) so symptoms stay data-describable — a
 * datapack symptom picks one of these. New side effects are added as new enum constants.
 */
public enum SymptomAction {
    /** No side effect — the symptom is purely its MobEffect (cough, sneezing, malaise). */
    NONE,
    /** Drain hunger and reset saturation (flu vomiting). Magnitudes live in the symptom service. */
    DRAIN_FOOD,
    /** Apply vanilla Nausea I as a brief burst (headache) — the screen-warp visual. */
    NAUSEA,
    /** Apply a brief, drastic Slowness (shortness of breath) — the movement-speed crash. */
    BREATHLESS,
    /** Repeating 10 s blindness + Slowness IV every 30 s — handled by {@link HypotensionEffect}. */
    HYPOTENSION,
    /** Repeated magic damage during a Bloody Coughing episode via BloodyCoughingEffect. */
    DAMAGE;

    public static final Codec<SymptomAction> CODEC = Codec.STRING.xmap(
        s -> valueOf(s.toUpperCase(Locale.ROOT)),
        a -> a.name().toLowerCase(Locale.ROOT)
    );
}
