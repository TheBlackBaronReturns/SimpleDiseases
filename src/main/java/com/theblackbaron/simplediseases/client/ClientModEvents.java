package com.theblackbaron.simplediseases.client;

import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipComponent;
import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipRenderer;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
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
        event.registerSpriteSet(DiseaseParticles.BLEEDING.get(), BleedingParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.VOMIT.get(), VomitParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.BLOODY_COUGH.get(), VomitParticle.Provider::new);
        event.registerSpriteSet(DiseaseParticles.SPUTUM.get(), VomitParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("bleeding",
                (gui, graphics, partialTick, width, height) -> BleedingHudOverlay.render(graphics, partialTick, width, height));
    }

    @SubscribeEvent
    public static void registerTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(IconTextTooltipComponent.class, IconTextTooltipRenderer::new);
    }
}
