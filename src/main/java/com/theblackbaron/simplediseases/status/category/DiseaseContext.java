package com.theblackbaron.simplediseases.status.category;

import com.theblackbaron.simplediseases.status.manager.LingeringNorovirusManager;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;

/**
 * Per-tick inputs handed to a {@link DiseaseCategory#tick}. Recovery suppression is per exclusion
 * group. Complication worsening is tracked separately so "too cold to recover" stalls complications
 * without making them accumulate. {@link #lingering} is the world-puddle manager.
 *
 * <p>{@link #suppressedEpisodeSources} is the set of source-disease IDs whose symptom episodes
 * should be skipped this tick — populated in DiseaseEvents when any of their complication children
 * has progressed past the first symptom threshold (0.1). Categories check this via
 * {@link #suppressEpisodes(ResourceLocation)} before calling {@code SymptomService.tickEpisodes}.
 */
public record DiseaseContext(
    ServerPlayer              player,
    Set<String>               suppressedRecoveryGroups,
    String                    complicationWorseningGroup,
    long                      gameTime,
    LingeringNorovirusManager lingering,
    PlayerDiseaseState        state,
    Set<ResourceLocation>     suppressedEpisodeSources
) {
    public boolean suppressRecovery(String exclusionGroup) {
        return suppressedRecoveryGroups.contains(exclusionGroup);
    }

    public boolean worsensComplication(String exclusionGroup) {
        return complicationWorseningGroup != null && complicationWorseningGroup.equals(exclusionGroup);
    }

    /** Returns true if the given source disease's episodes should be suppressed this tick. */
    public boolean suppressEpisodes(ResourceLocation sourceId) {
        return suppressedEpisodeSources.contains(sourceId);
    }
}
