package com.theblackbaron.simplediseases.status.def;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.category.DiseaseCategories;
import com.theblackbaron.simplediseases.status.category.DiseaseCategory;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Definition for a complication disease — one that develops only from the presence of another
 * (source) disease. Two gate modes are supported:
 *
 * <p><b>Viral gate</b> ({@code triggeredBy} absent): pre-latch accumulation while a qualifying
 * viral source is active at the right tier. Rate = 1 / {@code randomLatchTicks} ×
 * complicationMultiplier. Decay = {@code decayRate} when source drops. Absorbs the source on
 * latch, carries its contagion, and grants viral-group immunity on cure. Examples: pneumonia, bronchitis.
 *
 * <p><b>Bacterial gate</b> ({@code triggeredBy} present): pre-latch accumulation while the named
 * source bacterial disease is latched at its {@code progressCap} and max tier. Rate = {@code
 * accumulationRate}. Decay = {@code decayRate} when gate closes. Absorbs the source on latch.
 * Example: staph sepsis triggered by staph cellulitis.
 *
 * <p>Worsening model is per-def: {@code worseningRate == 0} uses stochastic momentum (chance =
 * min(1.0, 0.30 + 0.25 × worsenings)); {@code worseningRate > 0} uses deterministic threshold
 * advancement driven by {@code worseningRate} + {@code worseningThresholds}.
 *
 * <p>Recovery post-latch: if {@code passiveRecoveryRate} is present, use that fixed rate. Otherwise
 * viral-gate complications inherit the source's recovery rate; bacterial-gate ones have no recovery.
 *
 * <p>{@code deterministicWorsening()} is derived: returns true when {@code worseningRate > 0}.
 * {@code worsensKey()} is derived: {@code "message.simplediseases." + id.getPath() + "_worsens"}.
 */
public record ComplicationDiseaseDef(
    ResourceLocation   id,
    int                tierCount,
    ConditionType      organGroup,
    String             pathogenType,
    double             progressCap,
    double             latchThreshold,
    long               minLatchTicks,
    long               maxLatchTicks,
    SymptomConfig      symptoms,
    String             caughtKey,
    String             curedKey,
    // Gate / accumulation
    Optional<String>   triggeredBy,           // if present: bacterial gate (source at cap+max tier)
    double             decayRate,             // pre-latch decay rate when gate is inactive
    Optional<Double>   accumulationRate,      // bacterial-gate explicit rate; viral-gate uses 1/latchTicks
    // Recovery
    Optional<Double>   passiveRecoveryRate,   // post-latch recovery; empty = source-derived or none
    // Worsening (derived helpers below)
    double             worseningRate,         // > 0 → deterministic threshold model; 0 → stochastic momentum
    List<Double>       worseningThresholds    // worsening trigger points (stochastic or deterministic)
) implements DiseaseDef {

    private static final Map<String, ResourceLocation> TRIGGERED_BY_CACHE = new HashMap<>();

    /** True when {@code worseningRate > 0} (deterministic threshold model); false = stochastic momentum. */
    public boolean deterministicWorsening() { return worseningRate > 0.0; }

    /**
     * The fully-qualified {@link ResourceLocation} for {@link #triggeredBy()}, pre-computed and cached
     * so callers on the per-tick path don't allocate a new instance on every invocation.
     */
    public Optional<ResourceLocation> triggeredById() {
        return triggeredBy.map(path ->
            TRIGGERED_BY_CACHE.computeIfAbsent(path, p -> new ResourceLocation(SimpleDiseases.MOD_ID, p)));
    }

    /** Translation key for the "condition worsens" message, derived from the disease id path. */
    public String worsensKey() { return "message.simplediseases." + id().getPath() + "_worsens"; }

    @Override
    public DiseaseCategory category() {
        return DiseaseCategories.COMPLICATION;
    }

    public List<Severity> tiers() {
        return Severity.window(tierCount);
    }

    public Severity lowestSeverity() {
        return tiers().get(0);
    }

    // 17 logical fields exceeds RecordCodecBuilder's 16-arg group() limit, so min/max latch ticks —
    // already a natural pair — are packed into one mapPair slot and unpacked in the apply lambda below.
    public static final MapCodec<ComplicationDiseaseDef> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(ComplicationDiseaseDef::id),
        Codec.INT.fieldOf("tier_count").forGetter(ComplicationDiseaseDef::tierCount),
        ConditionType.CODEC.fieldOf("organ_group").forGetter(ComplicationDiseaseDef::organGroup),
        Codec.STRING.fieldOf("pathogen_type").forGetter(ComplicationDiseaseDef::pathogenType),
        Codec.DOUBLE.fieldOf("progress_cap").forGetter(ComplicationDiseaseDef::progressCap),
        Codec.DOUBLE.fieldOf("latch_threshold").forGetter(ComplicationDiseaseDef::latchThreshold),
        Codec.mapPair(Codec.LONG.fieldOf("min_latch_ticks"), Codec.LONG.fieldOf("max_latch_ticks"))
            .forGetter(cdef -> Pair.of(cdef.minLatchTicks(), cdef.maxLatchTicks())),
        SymptomConfig.CODEC.fieldOf("symptoms").forGetter(ComplicationDiseaseDef::symptoms),
        Codec.STRING.fieldOf("caught_message").forGetter(ComplicationDiseaseDef::caughtKey),
        Codec.STRING.fieldOf("cured_message").forGetter(ComplicationDiseaseDef::curedKey),
        Codec.STRING.optionalFieldOf("triggered_by").forGetter(ComplicationDiseaseDef::triggeredBy),
        Codec.DOUBLE.optionalFieldOf("decay_rate", 1.0 / 12000.0).forGetter(ComplicationDiseaseDef::decayRate),
        Codec.DOUBLE.optionalFieldOf("accumulation_rate").forGetter(ComplicationDiseaseDef::accumulationRate),
        Codec.DOUBLE.optionalFieldOf("passive_recovery_rate").forGetter(ComplicationDiseaseDef::passiveRecoveryRate),
        Codec.DOUBLE.optionalFieldOf("worsening_rate", 0.0).forGetter(ComplicationDiseaseDef::worseningRate),
        Codec.DOUBLE.listOf().optionalFieldOf("worsening_thresholds", List.of()).forGetter(ComplicationDiseaseDef::worseningThresholds)
    ).apply(i, (id, tierCount, organGroup, pathogenType, progressCap, latchThreshold, latchTicks, symptoms,
                caughtKey, curedKey, triggeredBy, decayRate, accumulationRate, passiveRecoveryRate,
                worseningRate, worseningThresholds) ->
        new ComplicationDiseaseDef(id, tierCount, organGroup, pathogenType, progressCap, latchThreshold,
                latchTicks.getFirst(), latchTicks.getSecond(), symptoms, caughtKey, curedKey, triggeredBy,
                decayRate, accumulationRate, passiveRecoveryRate, worseningRate, worseningThresholds)));
}
