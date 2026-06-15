package com.theblackbaron.simplediseases.compat;

import net.minecraft.world.level.Level;
import net.minecraftforge.fml.ModList;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

public class SereneSeasonsCompat {
    public static final boolean LOADED = ModList.get().isLoaded("sereneseasons");

    /** Current season, or null for dimensions Serene Seasons doesn't track (Nether/End, modded dims). */
    public static Season getCurrentSeason(Level level) {
        ISeasonState state = SeasonHelper.getSeasonState(level);
        return state == null ? null : state.getSeason();
    }

    /**
     * True when the player's dimension is in a cold season (winter or autumn). Returns false for
     * dimensions Serene Seasons doesn't track (e.g. the Nether/End, modded dims) — those return a
     * null season state. Check {@link #LOADED} too.
     */
    public static boolean isWinterOrAutumn(Level level) {
        ISeasonState state = SeasonHelper.getSeasonState(level);
        if (state == null) return false;
        Season season = state.getSeason();
        return season == Season.WINTER || season == Season.AUTUMN;
    }

    /** True only in winter (and only with Serene Seasons loaded + a tracked dimension). */
    public static boolean isWinter(Level level) {
        if (!LOADED) return false;
        ISeasonState state = SeasonHelper.getSeasonState(level);
        return state != null && state.getSeason() == Season.WINTER;
    }

    /** Season-cycle length in ticks, or 0 for dimensions Serene Seasons doesn't track. */
    public static int getCycleDuration(Level level) {
        ISeasonState state = SeasonHelper.getSeasonState(level);
        return state == null ? 0 : state.getCycleDuration();
    }
}
