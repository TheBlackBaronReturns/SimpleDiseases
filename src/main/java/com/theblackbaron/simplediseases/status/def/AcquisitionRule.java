package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Declares how a viral disease is picked when a damp/windchill exposure event occurs. One disease in
 * each exclusion group is the {@code isDefault} fallback (cold); the others each get a context-aware
 * chance to be chosen "instead of" the default. The engine tries the non-default diseases first; the
 * first whose roll succeeds wins, otherwise the default is accumulated.
 *
 * <p>Examples: flu = {@code requiresOutbreak, baseChance 0.6}; RSV = {@code excludedDuringFluSeason,
 * baseChance 0.2, winterChance 0.4}; cold = {@code isDefault}.
 */
public record AcquisitionRule(
    boolean isDefault,               // the fallback for the group (never rolled; taken if nothing else hits)
    boolean requiresOutbreak,        // only during an active flu outbreak
    boolean excludedDuringFluSeason, // never while the flu season window is open
    double  baseChance,              // chance to be chosen instead of the default
    double  winterChance             // chance during Serene Seasons winter (0 = fall back to baseChance)
) {
    public static final Codec<AcquisitionRule> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("default", false).forGetter(AcquisitionRule::isDefault),
        Codec.BOOL.optionalFieldOf("requires_outbreak", false).forGetter(AcquisitionRule::requiresOutbreak),
        Codec.BOOL.optionalFieldOf("excluded_during_flu_season", false).forGetter(AcquisitionRule::excludedDuringFluSeason),
        Codec.DOUBLE.optionalFieldOf("base_chance", 0.0).forGetter(AcquisitionRule::baseChance),
        Codec.DOUBLE.optionalFieldOf("winter_chance", 0.0).forGetter(AcquisitionRule::winterChance)
    ).apply(i, AcquisitionRule::new));

    /** True if this disease is never acquired via the damp/windchill route (no default, no chance in any
     *  context) — e.g. norovirus, which is waterborne-only. Such diseases are excluded from the
     *  damp/windchill fresh-start picker but still participate in the shared viral mutual-exclusion. */
    public boolean isInert() {
        return !isDefault && baseChance <= 0.0 && winterChance <= 0.0;
    }

    /** Effective chance to be chosen for an exposure event in the given context (0 = not eligible). */
    public double chance(boolean winter, boolean fluWindowOpen, boolean outbreakActive) {
        if (isDefault) return 0.0;
        if (requiresOutbreak && !outbreakActive) return 0.0;
        if (excludedDuringFluSeason && fluWindowOpen) return 0.0;
        return (winterChance > 0.0 && winter) ? winterChance : baseChance;
    }

    public static AcquisitionRule defaultDisease() {
        return new AcquisitionRule(true, false, false, 0.0, 0.0);
    }
}
