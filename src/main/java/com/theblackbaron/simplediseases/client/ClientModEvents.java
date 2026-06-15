package com.theblackbaron.simplediseases.client;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = SimpleDiseases.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(DiseaseParticles.COLD.get(), ColdParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.FLU.get(), FluParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.RSV.get(), RsvParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.NOROVIRUS.get(), NorovirusParticle.Provider::new);
    }
}
