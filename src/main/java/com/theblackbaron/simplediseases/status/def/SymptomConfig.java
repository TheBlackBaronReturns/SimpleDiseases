package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * The symptom side of a disease definition: the symptom pool, the progress thresholds at which the
 * pool grows, and the recovery-phase episode pacing (interval and duration ranges, in ticks).
 */
public record SymptomConfig(
    List<SymptomEntry> pool,
    List<Double>       thresholds,
    int minIntervalTicks,
    int maxIntervalTicks,
    int minDurationTicks,
    int maxDurationTicks
) {
    public static final Codec<SymptomConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        SymptomEntry.CODEC.listOf().fieldOf("pool").forGetter(SymptomConfig::pool),
        Codec.DOUBLE.listOf().fieldOf("thresholds").forGetter(SymptomConfig::thresholds),
        Codec.INT.fieldOf("min_interval").forGetter(SymptomConfig::minIntervalTicks),
        Codec.INT.fieldOf("max_interval").forGetter(SymptomConfig::maxIntervalTicks),
        Codec.INT.fieldOf("min_duration").forGetter(SymptomConfig::minDurationTicks),
        Codec.INT.fieldOf("max_duration").forGetter(SymptomConfig::maxDurationTicks)
    ).apply(i, SymptomConfig::new));

    public int symptomBits() { return pool.size(); }
}
