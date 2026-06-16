package com.theblackbaron.simplediseases.client;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only disease visuals. Body shivering reuses vanilla powdered-snow shake via
 * {@link com.theblackbaron.simplediseases.mixin.LivingEntityRendererMixin}; fever diseases suppress
 * the frostbite overlay since they are not cold exposure.
 */
@Mod.EventBusSubscriber(modid = SimpleDiseases.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.FROSTBITE.id())) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (DiseaseEffects.hasShiveringDisease(player)) {
            event.setCanceled(true);
        }
    }
}
