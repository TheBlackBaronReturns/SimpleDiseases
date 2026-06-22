package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;

import java.util.Locale;

/**
 * Maps disease severity to persistent Pain amplifier (0 = Mild … 4 = Excruciating).
 * Applied by {@link com.theblackbaron.simplediseases.status.service.PersistentEffectService} while latched.
 */
public enum PainProfile {
    /** Always Mild Pain — bronchitis, influenza. */
    MILD_FLAT,
    /** Mild/Moderate → Acute; Severe/Debilitating → Intense. */
    PNEUMONIA,
    /** Mild/Moderate → Acute; Severe → Intense. */
    CELLULITIS,
    /** Mild/Moderate → Intense; Severe/Debilitating → Severe. */
    SEPSIS;

    public static final Codec<PainProfile> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), p -> p.name().toLowerCase(Locale.ROOT));

    /** Pain MobEffect amplifier for the given disease tier, or 0 when {@code severity} is null. */
    public int amplifierFor(Severity severity) {
        if (severity == null) return 0;
        return switch (this) {
            case MILD_FLAT -> 0;
            case PNEUMONIA -> severity.ordinal() >= Severity.SEVERE.ordinal() ? 2 : 1;
            case CELLULITIS -> severity.ordinal() >= Severity.SEVERE.ordinal() ? 2 : 1;
            case SEPSIS -> severity.ordinal() >= Severity.SEVERE.ordinal() ? 3 : 2;
        };
    }
}
