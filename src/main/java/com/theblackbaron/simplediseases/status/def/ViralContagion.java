package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Contagion tuning for a viral disease — the parameters the contagion engine reads for one disease.
 * (Cold and flu differ only by these numbers; flu is the more aggressive row.)
 */
public record ViralContagion(
    double radius,
    float  playerTransmissionChance,
    double transmissionBump,
    int    villagerExposureThreshold,
    float  villagerVChance,
    float  generationDecay,
    float  transmissionDecay,
    int    villagerEffectTicks,
    int    villagerImmunityTicks,
    float  villagerSpawnSickChance,      // chance an adult villager spawns already infected
    float  villagerBabySpawnSickChance,  // chance a baby villager spawns/born already infected
    boolean playerContagious,            // whether this disease spreads player↔player via the generic engine
    boolean villagerContagious           // whether this disease spreads to/among/from villagers at all
) {
    public static final Codec<ViralContagion> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("radius").forGetter(ViralContagion::radius),
        Codec.FLOAT.fieldOf("player_chance").forGetter(ViralContagion::playerTransmissionChance),
        Codec.DOUBLE.fieldOf("transmission_bump").forGetter(ViralContagion::transmissionBump),
        Codec.INT.fieldOf("villager_exposure_threshold").forGetter(ViralContagion::villagerExposureThreshold),
        Codec.FLOAT.fieldOf("villager_v_chance").forGetter(ViralContagion::villagerVChance),
        Codec.FLOAT.fieldOf("generation_decay").forGetter(ViralContagion::generationDecay),
        Codec.FLOAT.fieldOf("transmission_decay").forGetter(ViralContagion::transmissionDecay),
        Codec.INT.fieldOf("villager_effect_ticks").forGetter(ViralContagion::villagerEffectTicks),
        Codec.INT.fieldOf("villager_immunity_ticks").forGetter(ViralContagion::villagerImmunityTicks),
        Codec.FLOAT.fieldOf("villager_spawn_sick_chance").forGetter(ViralContagion::villagerSpawnSickChance),
        Codec.FLOAT.optionalFieldOf("villager_baby_spawn_sick_chance", 0.0f).forGetter(ViralContagion::villagerBabySpawnSickChance),
        Codec.BOOL.optionalFieldOf("player_contagious", true).forGetter(ViralContagion::playerContagious),
        Codec.BOOL.optionalFieldOf("villager_contagious", true).forGetter(ViralContagion::villagerContagious)
    ).apply(i, ViralContagion::new));
}
