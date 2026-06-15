package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.world.entity.player.Player;

/**
 * Player-only immunity tiering for disease accumulation. The tier is determined by the active
 * MobEffect on the player; without either effect the player runs at the default tier.
 *
 * <p>Apply {@link #getDampMultiplier} to environmental cold/flu accumulation while Damp, and
 * {@link #getContagionMultiplier} to any per-bump contagion progress added from P→P or V→P
 * transmission. Villager-side spread and outbreak seeding are intentionally untouched.
 */
public final class ImmuneManager {
    // Tunable. Boosted dampness is 0 so a boosted player can never catch cold/flu from rain;
    // boosted contagion is small but non-zero so prolonged exposure can still infect.
    private static final double DAMP_DEFICIENT_MULT     = 1.00;
    private static final double DAMP_DEFAULT_MULT       = 0.50;
    private static final double DAMP_BOOSTED_MULT       = 0.00;

    private static final double CONTAGION_DEFICIENT_MULT = 1.00;
    private static final double CONTAGION_DEFAULT_MULT   = 0.50;
    private static final double CONTAGION_BOOSTED_MULT   = 0.20;

    // Windchill multipliers. DEFAULT is 1.0 so the 20-min unmitigated latch holds for a healthy
    // player; BOOSTED 0.0 means a boosted player never accumulates from wind; DEFICIENT 1.5 makes
    // immunodeficiency reach a cold in ~13.3 min. (Not reusing the damp 0.5 default, which would
    // break the 20-min calibration.)
    private static final double WINDCHILL_DEFICIENT_MULT = 1.50;
    private static final double WINDCHILL_DEFAULT_MULT   = 1.00;
    private static final double WINDCHILL_BOOSTED_MULT   = 0.00;

    // Waterborne (norovirus reservoir) multipliers. DEFAULT is 1.0 so the calibrated ~5-min wading /
    // ~2.5-min submerged latch holds for a healthy player. BOOSTED is small but NON-ZERO (unlike damp/
    // windchill's hard 0.0 block) — a boosted-immunity player still catches it from infected water,
    // just ~5× slower (~25 min wading). DEFICIENT 1.5 makes immunodeficiency catch it faster.
    private static final double WATERBORNE_DEFICIENT_MULT = 1.50;
    private static final double WATERBORNE_DEFAULT_MULT   = 1.00;
    private static final double WATERBORNE_BOOSTED_MULT   = 0.20;

    // Viral-complication (pneumonia) accumulation scale. Immunodeficiency develops it faster (and has
    // extra qualifying conditions); boosted immunity develops it slower. DEFAULT keeps the 15–30 min base.
    private static final double COMPLICATION_DEFICIENT_MULT = 1.50;
    private static final double COMPLICATION_DEFAULT_MULT   = 1.00;
    private static final double COMPLICATION_BOOSTED_MULT   = 0.40;

    // Severity-roll bias: immunodeficiency rolls the tier this many times and keeps the MOST severe,
    // so illnesses skew worse. Default (and boosted) roll once — no skew. Tune the deficient count up
    // for a harsher skew toward severe/debilitating.
    private static final int SEVERITY_ROLLS_DEFICIENT = 2;
    private static final int SEVERITY_ROLLS_DEFAULT   = 1;

    private ImmuneManager() {}

    public static double getDampMultiplier(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return DAMP_BOOSTED_MULT;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return DAMP_DEFICIENT_MULT;
        return DAMP_DEFAULT_MULT;
    }

    public static double getContagionMultiplier(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return CONTAGION_BOOSTED_MULT;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return CONTAGION_DEFICIENT_MULT;
        return CONTAGION_DEFAULT_MULT;
    }

    public static double getWindchillMultiplier(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return WINDCHILL_BOOSTED_MULT;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return WINDCHILL_DEFICIENT_MULT;
        return WINDCHILL_DEFAULT_MULT;
    }

    /** Waterborne (norovirus reservoir) accumulation scale. Unlike damp/windchill, BOOSTED is non-zero
     *  — a boosted player still catches norovirus from infected water, just much more slowly. */
    public static double getWaterborneMultiplier(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return WATERBORNE_BOOSTED_MULT;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return WATERBORNE_DEFICIENT_MULT;
        return WATERBORNE_DEFAULT_MULT;
    }

    /** How many weighted tier rolls to take the most-severe of when an illness latches — &gt;1 skews
     *  toward worse tiers. Immunodeficiency raises it; default/boosted roll once. */
    public static int getSeverityRolls(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return SEVERITY_ROLLS_DEFICIENT;
        return SEVERITY_ROLLS_DEFAULT;
    }

    /** Pneumonia accumulation scale — boosted immunity slows it, immunodeficiency speeds it. */
    public static double getComplicationMultiplier(Player player) {
        if (player.hasEffect(DiseaseEffects.IMMUNE.get()))            return COMPLICATION_BOOSTED_MULT;
        if (player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) return COMPLICATION_DEFICIENT_MULT;
        return COMPLICATION_DEFAULT_MULT;
    }

    public static boolean isImmunodeficient(Player player) {
        return player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get());
    }
}
