package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

/** Infinite malaise and/or Pain applied while a disease is latched — not in the symptom pool. */
public record PersistentEffects(boolean malaise, Optional<PainProfile> painProfile) {

    public static final PersistentEffects NONE = new PersistentEffects(false, Optional.empty());

    public static PersistentEffects malaiseOnly() {
        return new PersistentEffects(true, Optional.empty());
    }

    public static PersistentEffects withPain(PainProfile profile) {
        return new PersistentEffects(true, Optional.of(profile));
    }

    public boolean hasPain() {
        return painProfile.isPresent();
    }

    public static final Codec<PersistentEffects> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.BOOL.optionalFieldOf("malaise", false).forGetter(PersistentEffects::malaise),
        PainProfile.CODEC.optionalFieldOf("pain_profile").forGetter(PersistentEffects::painProfile)
    ).apply(i, PersistentEffects::new));
}
