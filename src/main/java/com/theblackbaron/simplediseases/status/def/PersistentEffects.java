package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;
import java.util.OptionalInt;

/** Infinite malaise and/or Pain applied while a disease is latched — not in the symptom pool. */
public record PersistentEffects(boolean malaise, OptionalInt painAmplifier) {

    public static final PersistentEffects NONE = new PersistentEffects(false, OptionalInt.empty());

    public static PersistentEffects malaiseOnly() {
        return new PersistentEffects(true, OptionalInt.empty());
    }

    public static PersistentEffects withPain(int amplifier) {
        return new PersistentEffects(true, OptionalInt.of(amplifier));
    }

    public static final Codec<PersistentEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("malaise", false).forGetter(PersistentEffects::malaise),
        Codec.INT.optionalFieldOf("pain_amplifier").forGetter(p ->
                p.painAmplifier().isPresent() ? Optional.of(p.painAmplifier().getAsInt()) : Optional.empty())
    ).apply(i, (malaise, pain) -> new PersistentEffects(malaise,
            pain.map(OptionalInt::of).orElse(OptionalInt.empty()))));
}
