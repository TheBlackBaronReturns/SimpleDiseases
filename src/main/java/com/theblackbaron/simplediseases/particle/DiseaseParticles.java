package com.theblackbaron.simplediseases.particle;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class DiseaseParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, SimpleDiseases.MOD_ID);

    public static final RegistryObject<SimpleParticleType> COLD =
            PARTICLES.register("cold", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> FLU =
            PARTICLES.register("flu", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> RSV =
            PARTICLES.register("rsv", () -> new SimpleParticleType(false));

    public static final RegistryObject<SimpleParticleType> NOROVIRUS =
            PARTICLES.register("norovirus", () -> new SimpleParticleType(false));
}
