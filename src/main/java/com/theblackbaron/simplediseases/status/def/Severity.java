package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The global disease-severity scale, centered on {@link #MODERATE} = today's tuning. Every per-tier
 * value (symptom duration, episode interval, attribute-debuff strength) is a multiplier relative to
 * Moderate, and each tier carries a roll weight (weighted toward the mild end). A disease occupies a
 * {@link #window} of this scale (3-tier → Mild/Moderate/Severe, 4-tier → Mild…Debilitating); MODERATE
 * is always a member — the universal default used for villagers and pre-latch symptoms. LIGHT is not
 * currently used by any built-in disease (cold/rsv are 3-tier, flu is 4-tier; all start at Mild).
 */
public enum Severity {
    LIGHT       (0.40, 1.70, 0.40, 35),
    MILD        (0.65, 1.30, 0.70, 30),
    MODERATE    (1.00, 1.00, 1.00, 20),
    SEVERE      (1.50, 0.65, 1.40, 11),
    DEBILITATING(2.20, 0.40, 1.90,  4);

    public final double durationMult;  // symptom episode length ×, relative to Moderate
    public final double intervalMult;  // episode spacing × (higher tier → smaller → more frequent)
    public final double debuffMult;    // attribute-modifier strength ×
    public final int    weight;        // roll likelihood

    Severity(double durationMult, double intervalMult, double debuffMult, int weight) {
        this.durationMult = durationMult;
        this.intervalMult = intervalMult;
        this.debuffMult   = debuffMult;
        this.weight       = weight;
    }

    private static final Severity[] ORDER = values();

    /** Serializes by lowercase name (e.g. "severe") — used by codec'd fields like SymptomEntry's gate. */
    public static final Codec<Severity> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), Severity::id);

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public int scaleDuration(int base) { return Math.max(1, (int) Math.round(base * durationMult)); }
    public int scaleInterval(int base) { return Math.max(1, (int) Math.round(base * intervalMult)); }

    /**
     * The window of {@code count} tiers centered on MODERATE; for even counts the extra tier is taken
     * on the severe side. So 3 → Mild/Moderate/Severe, 4 → Mild/Moderate/Severe/Debilitating, 5 → all.
     * MODERATE is always a member (the universal middle used for villagers and pre-latch symptoms).
     */
    public static List<Severity> window(int count) {
        int mid  = MODERATE.ordinal();
        int low  = Math.max(0, mid - (count - 1) / 2);
        int high = Math.min(ORDER.length - 1, mid + count / 2);
        List<Severity> tiers = new ArrayList<>(count);
        for (int i = low; i <= high; i++) tiers.add(ORDER[i]);
        return tiers;
    }

    public static Severity byOrdinal(int ordinal) {
        return (ordinal >= 0 && ordinal < ORDER.length) ? ORDER[ordinal] : null;
    }

    /** Weighted pick over a tier window (weighted toward milder severities by {@link #weight}). */
    public static Severity rollWeighted(List<Severity> tiers, RandomSource rng) {
        int total = 0;
        for (Severity s : tiers) total += s.weight;
        int r = rng.nextInt(Math.max(1, total));
        for (Severity s : tiers) {
            if (r < s.weight) return s;
            r -= s.weight;
        }
        return MODERATE;
    }

    /** {@code rolls} weighted picks, keeping the MOST severe — so {@code rolls > 1} skews the result
     *  toward worse tiers (used for immunodeficiency). {@code rolls <= 1} is a single normal pick. */
    public static Severity rollWeightedBiased(List<Severity> tiers, RandomSource rng, int rolls) {
        Severity worst = rollWeighted(tiers, rng);
        for (int i = 1; i < rolls; i++) {
            Severity r = rollWeighted(tiers, rng);
            if (r.ordinal() > worst.ordinal()) worst = r;
        }
        return worst;
    }
}
