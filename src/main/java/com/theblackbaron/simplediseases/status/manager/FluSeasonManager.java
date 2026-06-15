package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import sereneseasons.api.season.Season;

public class FluSeasonManager {
    private static final Season[] FLU_SEASONS = {Season.AUTUMN, Season.WINTER};
    private static final int[]    FLU_WEIGHTS = {40, 60};

    // Vanilla fallback: 24 in-game days per year, matching Serene Seasons' default cycle.
    private static final long  VANILLA_YEAR_TICKS   = 576000L;
    private static final long  VANILLA_SEASON_TICKS = VANILLA_YEAR_TICKS / 4;

    // Probability that flu season actually produces an outbreak. Forced flu season always outbreaks.
    private static final float OUTBREAK_CHANCE = 0.60f;

    private FluSeasonData data           = null; // loaded on first tick, persisted via SavedData
    private boolean       forceFluSeason = false;
    private boolean       pendingOutbreak = false;

    public boolean isFluWindowOpen(Level level) {
        if (forceFluSeason) return true;
        if (data == null) return false;
        if (SereneSeasonsCompat.LOADED) {
            return data.fluSeason != null && SereneSeasonsCompat.getCurrentSeason(level) == data.fluSeason;
        }
        long t = level.getGameTime();
        return data.fluWindowStart >= 0 && t >= data.fluWindowStart && t < data.fluWindowEnd;
    }

    /** True only when the window is open AND this year's outbreak roll passed (or it's forced).
     *  Use this for player-facing effects — quiet years behave like non-flu season. */
    public boolean isOutbreakActive(Level level) {
        return data != null && data.outbreakActive && isFluWindowOpen(level);
    }

    public Season  getFluSeason()      { return data != null ? data.fluSeason : null; }
    public boolean isForcedFluSeason() { return forceFluSeason; }

    public boolean toggleForceFluSeason() {
        forceFluSeason = !forceFluSeason;
        if (forceFluSeason && data != null) {
            data.fluOutbreakTriggered = false;
            data.setDirty();
        }
        return forceFluSeason;
    }

    /** Returns true exactly once when flu season begins each year, then resets. */
    public boolean consumeOutbreak() {
        boolean b = pendingOutbreak;
        pendingOutbreak = false;
        return b;
    }

    public void tick(ServerLevel overworld) {
        if (data == null) data = FluSeasonData.getOrCreate(overworld);

        if (SereneSeasonsCompat.LOADED) {
            int cycleDuration = SereneSeasonsCompat.getCycleDuration(overworld);
            int year = cycleDuration > 0 ? (int)(overworld.getGameTime() / cycleDuration) : 0;
            if (year != data.currentYear) {
                data.currentYear = year;
                data.fluSeason = pickFluSeason(overworld.getRandom());
                data.fluOutbreakTriggered = false;
                data.outbreakActive = false;
                data.setDirty();
            }
        } else {
            int year = (int)(overworld.getGameTime() / VANILLA_YEAR_TICKS);
            if (year != data.currentYear) {
                data.currentYear = year;
                long yearStart    = (long) year * VANILLA_YEAR_TICKS;
                // 40% → third quarter (autumn equivalent), 60% → fourth quarter (winter equivalent)
                int  roll         = overworld.getRandom().nextInt(100);
                long seasonOffset = roll < 40 ? VANILLA_SEASON_TICKS * 2 : VANILLA_SEASON_TICKS * 3;
                // Label the window with the equivalent season for display (getFluSeason / debug tag);
                // the open/closed test in vanilla mode uses the window ticks, not this field.
                data.fluSeason         = roll < 40 ? Season.AUTUMN : Season.WINTER;
                data.fluWindowStart    = yearStart + seasonOffset;
                data.fluWindowEnd      = data.fluWindowStart + VANILLA_SEASON_TICKS;
                data.fluOutbreakTriggered = false;
                data.outbreakActive = false;
                data.setDirty();
            }
        }

        if (!data.fluOutbreakTriggered && isFluWindowOpen(overworld)) {
            data.fluOutbreakTriggered = true;
            if (forceFluSeason || overworld.getRandom().nextFloat() < OUTBREAK_CHANCE) {
                pendingOutbreak = true;
                data.outbreakActive = true;
            }
            data.setDirty();
        }
    }

    private Season pickFluSeason(net.minecraft.util.RandomSource random) {
        int roll = random.nextInt(100);
        int cumulative = 0;
        for (int i = 0; i < FLU_SEASONS.length; i++) {
            cumulative += FLU_WEIGHTS[i];
            if (roll < cumulative) return FLU_SEASONS[i];
        }
        return Season.WINTER;
    }
}
