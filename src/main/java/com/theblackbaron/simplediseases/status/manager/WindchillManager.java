package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.compat.ColdSweatCompat;
import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Dry-cold "windchill" accumulation. While a player stands exposed to the open sky in a cold
 * context (a cold/high-altitude biome, or — with Serene Seasons — a non-humid biome in
 * winter/autumn), disease progress accumulates every tick even when the player is completely dry —
 * a parallel to the existing Damp accumulation. The per-tick rate is reduced multiplicatively by
 * clothing, a hot Cold Sweat waterskin, physical activity, and immunity (see
 * {@link ImmuneManager#getWindchillMultiplier}).
 *
 * <p>The routing into cold-vs-flu progress (including the flu-season split) lives in
 * {@code DiseaseEvents.accumulate}; this class only decides whether windchill applies and how
 * strongly it is mitigated.
 */
public final class WindchillManager {

    /** Unmitigated cold-progress per tick: 1.0 over 20 minutes (20 × 60 × 20 = 24000 ticks). */
    public static final double BASE_RATE = 1.0 / 24000.0;

    /** While windchill conditions hold, the Chilly Wind effect pulses: applied for
     *  {@link #EFFECT_DURATION_TICKS}, once every {@link #EFFECT_PERIOD_TICKS} of sustained impact. */
    public static final int EFFECT_PERIOD_TICKS   = 30 * 20; // one pulse per 30 s of impact
    public static final int EFFECT_DURATION_TICKS = 15 * 20; // each pulse lasts 15 s

    // Mitigation tunables.
    private static final double INSULATION_PER_PIECE = 0.25; // each leather/goat-fur piece (full 4-piece set → rate 0)
    private static final double WATERSKIN_MULT       = 0.50; // carrying a hot filled waterskin
    private static final double ACTIVITY_MULT        = 0.60; // sprinting or recent combat (body heat)
    private static final int    COMBAT_RECENT_TICKS  = 100;  // 5 s window counts as "in combat"

    // Biomes at/below this base temperature are "cold" enough for windchill on their own (taiga
    // 0.25, snowy variants, windswept hills 0.2). Forest (0.7) / plains (0.8) are excluded.
    private static final float  COLD_BIOME_TEMP      = 0.30F;
    // Biomes with downfall above this are "humid" (jungles, swamps) and excluded from seasonal
    // windchill — matches vanilla Biome#isHumid's threshold.
    private static final float  HUMID_DOWNFALL       = 0.85F;

    private WindchillManager() {}

    /** True when the player is exposed to open sky in a cold context (cold biome, or SS winter/autumn). */
    public static boolean isInWindchill(ServerPlayer player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        // Exposed: must see open sky and not be sheltered by warmth/light (roof, fire, torches).
        if (!level.canSeeSky(pos)) return false;
        if (level.getBrightness(LightLayer.BLOCK, pos) > 7) return false;

        Biome biome = level.getBiome(pos).value();
        // Serene Seasons winter/autumn brings windchill to any non-humid biome (excludes jungles,
        // swamps and other humid/tropical biomes, which stay warm even in the cold seasons).
        boolean coldSeason = SereneSeasonsCompat.LOADED
                && SereneSeasonsCompat.isWinterOrAutumn(level)
                && biome.getModifiedClimateSettings().downfall() <= HUMID_DOWNFALL;
        // Cold-category biome (e.g. taiga) OR snow-cold at this position (covers high altitude in
        // otherwise-temperate biomes).
        boolean coldBiome = biome.getBaseTemperature() <= COLD_BIOME_TEMP || biome.coldEnoughToSnow(pos);
        return coldSeason || coldBiome;
    }

    /** Combined [0,1] mitigation factor from clothing, hot waterskin, and activity (multiplicative). */
    public static double getMitigationFactor(ServerPlayer player) {
        int insulationPieces = leatherArmorPieces(player) + ColdSweatCompat.goatFurArmorPieces(player);
        double clothingFactor  = Math.max(0.0, 1.0 - insulationPieces * INSULATION_PER_PIECE);
        double waterskinFactor = ColdSweatCompat.hasHotWaterskin(player) ? WATERSKIN_MULT : 1.0;
        double activityFactor  = isActive(player) ? ACTIVITY_MULT : 1.0;
        return clothingFactor * waterskinFactor * activityFactor;
    }

    private static int leatherArmorPieces(ServerPlayer player) {
        int count = 0;
        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.is(Items.LEATHER_HELMET) || stack.is(Items.LEATHER_CHESTPLATE)
                    || stack.is(Items.LEATHER_LEGGINGS) || stack.is(Items.LEATHER_BOOTS)) count++;
        }
        return count;
    }

    private static boolean isActive(ServerPlayer player) {
        if (player.isSprinting()) return true;
        int now = player.tickCount;
        return (player.getLastHurtMobTimestamp() > 0 && now - player.getLastHurtMobTimestamp() < COMBAT_RECENT_TICKS)
            || (player.getLastHurtByMobTimestamp() > 0 && now - player.getLastHurtByMobTimestamp() < COMBAT_RECENT_TICKS);
    }
}
