package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Waterborne norovirus reservoirs. Which patches of water are "infected" is a deterministic function
 * of position + world seed + a time epoch — like vanilla slime chunks, so there is no stored state, no
 * ticking, and no water-body scanning. A player standing in an infected region's water accumulates
 * norovirus progress directly (its own gastrointestinal exclusion group, not the respiratory
 * damp/windchill routing). This is the environmental "index case" route — it works with zero infected
 * entities in the world, and the contagion engine amplifies it from there.
 */
public final class WaterborneManager {
    private WaterborneManager() {}

    // Zone footprint = 1<<REGION_SHIFT blocks (5 → 32×32). ~1-in-DENOM regions are infected year-round;
    // in (Serene Seasons) winter, WINTER_EXTRA adds more on top — the "winter vomiting bug" peak.
    private static final int  REGION_SHIFT = 5;
    private static final long DENOM        = 20;
    private static final long WINTER_EXTRA = 12;
    // Infected regions rotate every epoch (168000 ticks = 7 in-game days). 0 = permanent reservoirs.
    private static final long EPOCH_TICKS  = 168000L;
    // Disease-specific salt so this never aligns with slime chunks or other positional features.
    private static final long SALT         = 0x4E4F524FL; // "NORO"
    // Norovirus progress per tick while wading in infected water (×2 when fully submerged).
    // 1/6000 → ~5 min wading / ~2.5 min submerged to latch at the 1.0 threshold. Drives both
    // the reservoir incubation (exposureRate) and the lingering-puddle incubation (submergedRate).
    private static final double WATER_EXPOSURE_RATE = 1.0 / 6000.0;

    /**
     * Whether the player is standing in an infected reservoir this tick. The per-player verdict is
     * cached and only recomputed on region/epoch/season change, so the hash runs rarely. This is a pure
     * query — the caller ({@link com.theblackbaron.simplediseases.event.DiseaseEvents}) owns the
     * acquisition routing (the clean switch off a sub-threshold respiratory disease, the norovirus
     * accumulation, and the shared viral mutual-exclusion).
     */
    public static boolean isInInfectedWater(ServerPlayer player, PlayerDiseaseState state, long gameTime) {
        if (!player.isInWater()) return false;
        if (!(player.level() instanceof ServerLevel level)) return false;

        long    epoch  = EPOCH_TICKS > 0 ? gameTime / EPOCH_TICKS : 0L;
        boolean winter = SereneSeasonsCompat.isWinter(level);
        long    region = packRegion(player.getBlockX(), player.getBlockZ());

        if (region == state.getLastWaterRegion() && epoch == state.getLastWaterEpoch()
                && winter == state.isLastWaterWinter()) {
            return state.isLastWaterInfected();
        }
        boolean infected = isInfectedRegion(player.getBlockX() >> REGION_SHIFT, player.getBlockZ() >> REGION_SHIFT,
                level.getSeed(), epoch, winter);
        state.cacheWaterVerdict(region, epoch, winter, infected);
        return infected;
    }

    /** Norovirus progress to add this tick while in an infected reservoir (doubled when submerged). */
    public static double exposureRate(ServerPlayer player) {
        return WATER_EXPOSURE_RATE * (player.isUnderWater() ? 2.0 : 1.0);
    }

    /** The flat "fully submerged" rate — lingering vomit puddles expose as if the player were submerged. */
    public static double submergedRate() {
        return WATER_EXPOSURE_RATE * 2.0;
    }

    /** The flat base wading rate (not submersion-scaled). The per-tick delivery step for a committed
     *  norovirus incubation bleeds the incubation into progress at this rate. */
    public static double baseExposureRate() {
        return WATER_EXPOSURE_RATE;
    }

    public static long packRegion(int blockX, int blockZ) {
        return ((long) (blockX >> REGION_SHIFT) << 32) ^ ((blockZ >> REGION_SHIFT) & 0xFFFFFFFFL);
    }

    /**
     * Locate a water block inside the nearest infected reservoir, for debug teleporting (see
     * {@code /sdreservoir}). Spirals outward over regions by Chebyshev distance — the hash test is cheap
     * and loads no chunks, so only regions that are actually infected get their columns scanned for water.
     * Returns the first surface-water block found in the nearest infected region that has any, or
     * {@code null} if none is found within {@code maxRegionRadius} (or after scanning {@code maxRegionScans}
     * infected regions, whichever comes first — the cap bounds chunk loading in waterless terrain).
     */
    public static BlockPos findNearestReservoirWater(ServerLevel level, BlockPos origin, long gameTime,
                                                     int maxRegionRadius, int maxRegionScans) {
        long    epoch    = EPOCH_TICKS > 0 ? gameTime / EPOCH_TICKS : 0L;
        boolean winter   = SereneSeasonsCompat.isWinter(level);
        long    seed     = level.getSeed();
        int     originRx = origin.getX() >> REGION_SHIFT;
        int     originRz = origin.getZ() >> REGION_SHIFT;

        int scanned = 0;
        for (int r = 0; r <= maxRegionRadius; r++) {
            for (int rx = originRx - r; rx <= originRx + r; rx++) {
                for (int rz = originRz - r; rz <= originRz + r; rz++) {
                    // Perimeter of the square ring at Chebyshev distance r only (inner rings already done).
                    if (Math.max(Math.abs(rx - originRx), Math.abs(rz - originRz)) != r) continue;
                    if (!isInfectedRegion(rx, rz, seed, epoch, winter)) continue;
                    BlockPos water = findWaterInRegion(level, rx, rz);
                    if (water != null) return water;
                    if (++scanned >= maxRegionScans) return null;
                }
            }
        }
        return null;
    }

    /** Sample columns across a region's footprint for a surface water block (every 2 blocks; checks a few
     *  blocks below the heightmap surface to catch water under ice/overhangs). */
    private static BlockPos findWaterInRegion(ServerLevel level, int rx, int rz) {
        int baseX = rx << REGION_SHIFT;
        int baseZ = rz << REGION_SHIFT;
        int size  = 1 << REGION_SHIFT;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = 0; dx < size; dx += 2) {
            for (int dz = 0; dz < size; dz += 2) {
                int x    = baseX + dx;
                int z    = baseZ + dz;
                int surf = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                int min  = Math.max(level.getMinBuildHeight(), surf - 6);
                for (int y = surf; y >= min; y--) {
                    p.set(x, y, z);
                    if (level.getBlockState(p).getFluidState().is(FluidTags.WATER)) {
                        return p.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Deterministic infected-region test: a SplitMix64-finalized hash of region+seed+epoch, with a
     *  winter superset so cold-season worlds simply have more reservoirs (existing ones stay hot). */
    private static boolean isInfectedRegion(int rx, int rz, long seed, long epoch, boolean winter) {
        long h = seed ^ SALT;
        h ^=  rx    * 0xBF58476D1CE4E5B9L;
        h ^= (rz    * 0x94D049BB133111EBL) << 1;
        h ^=  epoch * 0xD6E8FEB86659FD93L;
        h ^= h >>> 30; h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 27; h *= 0x94D049BB133111EBL;
        h ^= h >>> 31;
        if (Long.remainderUnsigned(h, DENOM) == 0L) return true;
        return winter && Long.remainderUnsigned(h, WINTER_EXTRA) == 0L;
    }
}
