package com.theblackbaron.simplediseases.status.manager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import sereneseasons.api.season.Season;

class FluSeasonData extends SavedData {
    private static final String DATA_NAME = "simplediseases_flu_season";

    int     currentYear          = Integer.MIN_VALUE;
    boolean fluOutbreakTriggered = false;
    boolean outbreakActive       = false;
    Season  fluSeason            = null;
    long    fluWindowStart       = -1L;
    long    fluWindowEnd         = -1L;

    static FluSeasonData load(CompoundTag tag) {
        FluSeasonData d = new FluSeasonData();
        d.currentYear          = tag.getInt("currentYear");
        d.fluOutbreakTriggered = tag.getBoolean("fluOutbreakTriggered");
        d.outbreakActive       = tag.getBoolean("outbreakActive");
        if (tag.contains("fluSeason")) {
            try { d.fluSeason = Season.valueOf(tag.getString("fluSeason")); }
            catch (IllegalArgumentException ignored) {}
        }
        d.fluWindowStart = tag.getLong("fluWindowStart");
        d.fluWindowEnd   = tag.getLong("fluWindowEnd");
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("currentYear",              currentYear);
        tag.putBoolean("fluOutbreakTriggered", fluOutbreakTriggered);
        tag.putBoolean("outbreakActive",       outbreakActive);
        if (fluSeason != null) tag.putString("fluSeason", fluSeason.name());
        tag.putLong("fluWindowStart", fluWindowStart);
        tag.putLong("fluWindowEnd",   fluWindowEnd);
        return tag;
    }

    static FluSeasonData getOrCreate(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
            FluSeasonData::load,
            FluSeasonData::new,
            DATA_NAME
        );
    }
}
