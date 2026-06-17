package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;

/** Episodic symptoms rotate through random episodes; static symptoms use infinite HUD markers. */
public enum SymptomTiming {
    EPISODIC,
    STATIC;

    public static final Codec<SymptomTiming> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase()), t -> t.name().toLowerCase());
}
