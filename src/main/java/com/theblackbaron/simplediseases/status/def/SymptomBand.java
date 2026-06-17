package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;

/**
 * Gates whether a symptom may enter the episodic pool when drawn. {@link #ADVANCED} requires
 * {@link Severity#SEVERE} or higher at draw time; once in the pool, advanced symptoms stay sticky.
 */
public enum SymptomBand {
    COMMON,
    ADVANCED;

    public static final Codec<SymptomBand> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase()), b -> b.name().toLowerCase());

    public boolean eligibleAt(Severity severity) {
        return this == COMMON || severity.ordinal() >= Severity.SEVERE.ordinal();
    }
}
