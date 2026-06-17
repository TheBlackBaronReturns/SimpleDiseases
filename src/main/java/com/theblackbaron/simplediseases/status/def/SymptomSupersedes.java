package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.effect.MobEffect;

import java.util.function.Supplier;

/** Optional tier-driven replacement of a common symptom with an advanced form (unused at bootstrap). */
public record SymptomSupersedes(Supplier<MobEffect> common, Supplier<MobEffect> advanced) {

    public static final Codec<SymptomSupersedes> CODEC = RecordCodecBuilder.create(i -> i.group(
        DiseaseCodecs.EFFECT.fieldOf("common").forGetter(SymptomSupersedes::common),
        DiseaseCodecs.EFFECT.fieldOf("advanced").forGetter(SymptomSupersedes::advanced)
    ).apply(i, SymptomSupersedes::new));
}
