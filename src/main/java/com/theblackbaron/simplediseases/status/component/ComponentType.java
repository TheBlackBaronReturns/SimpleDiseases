package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;

import java.util.function.Supplier;

/**
 * Registry key for a {@link DiseaseComponent} implementation. Bundles the NBT key the component
 * serializes under, its {@link Codec} (save/load now, network sync later), and a factory for a
 * fresh default instance. Compared by identity — declare each type once in {@link Components}.
 */
public final class ComponentType<T extends DiseaseComponent> {
    private final String nbtKey;
    private final Codec<T> codec;
    private final Supplier<T> factory;

    public ComponentType(String nbtKey, Codec<T> codec, Supplier<T> factory) {
        this.nbtKey  = nbtKey;
        this.codec   = codec;
        this.factory = factory;
    }

    public String    nbtKey()  { return nbtKey; }
    public Codec<T>  codec()   { return codec; }
    public T         create()  { return factory.get(); }
}
