package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

/** Shared codecs for disease definitions. */
public final class DiseaseCodecs {
    private DiseaseCodecs() {}

    /**
     * A MobEffect reference, serialized as its registry id. Decoded lazily as a {@link Supplier} so
     * code-built definitions can pass a Forge {@code RegistryObject} directly (effects aren't
     * resolved until first use); JSON-loaded definitions resolve at parse time, after registries.
     */
    public static final Codec<Supplier<MobEffect>> EFFECT = ResourceLocation.CODEC.xmap(
        rl -> {
            MobEffect effect = ForgeRegistries.MOB_EFFECTS.getValue(rl);
            return (Supplier<MobEffect>) () -> effect;
        },
        supplier -> ForgeRegistries.MOB_EFFECTS.getKey(supplier.get())
    );

    /**
     * A particle reference, serialized as its registry id. Simple particle types are their own
     * {@link ParticleOptions}, which is what the emitter needs. Lazy {@link Supplier} for the same
     * reason as {@link #EFFECT}.
     */
    public static final Codec<Supplier<ParticleOptions>> PARTICLE = ResourceLocation.CODEC.xmap(
        rl -> {
            ParticleType<?> type = ForgeRegistries.PARTICLE_TYPES.getValue(rl);
            ParticleOptions options = (type instanceof ParticleOptions po) ? po : null;
            return (Supplier<ParticleOptions>) () -> options;
        },
        supplier -> ForgeRegistries.PARTICLE_TYPES.getKey(supplier.get().getType())
    );

    /** A SoundEvent reference, serialized as its registry id. Lazy, like {@link #EFFECT}. */
    public static final Codec<Supplier<SoundEvent>> SOUND = ResourceLocation.CODEC.xmap(
        rl -> {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(rl);
            return (Supplier<SoundEvent>) () -> sound;
        },
        supplier -> ForgeRegistries.SOUND_EVENTS.getKey(supplier.get())
    );
}
