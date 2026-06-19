package com.theblackbaron.simplediseases.client;

import com.mojang.datafixers.util.Either;
import com.mojang.blaze3d.systems.RenderSystem;
import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipComponent;
import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipRenderer;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Client-only disease visuals. Body shivering reuses vanilla powdered-snow shake via
 * {@link com.theblackbaron.simplediseases.mixin.LivingEntityRendererMixin}; fever diseases suppress
 * the frostbite overlay since they are not cold exposure.
 */
@Mod.EventBusSubscriber(modid = SimpleDiseases.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        BleedingHudOverlay.tick();
    }

    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            if (DiseaseEffects.hasStomachCramps(player)) {
                RenderSystem.setShaderColor(1.0F, 0.35F, 0.35F, 1.0F);
            }
            if (DiseaseEffects.hasTachypnea(player)) {
                event.getGuiGraphics().pose().pushPose();
                event.getGuiGraphics().pose().translate(0.0F, -TachypneaLungOverlay.foodRowShift(), 0.0F);
            }
        }

        if (event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())
                && DiseaseEffects.hasTachypnea(player)) {
            event.setCanceled(true);
            return;
        }

        if (!event.getOverlay().id().equals(VanillaGuiOverlay.FROSTBITE.id())) return;
        if (DiseaseEffects.hasShiveringDisease(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (event.getOverlay().id().equals(VanillaGuiOverlay.FOOD_LEVEL.id())) {
            if (DiseaseEffects.hasStomachCramps(player)) {
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            }
            if (DiseaseEffects.hasTachypnea(player)) {
                event.getGuiGraphics().pose().popPose();
            }
            return;
        }

        if (event.getOverlay().id().equals(VanillaGuiOverlay.AIR_LEVEL.id())
                && DiseaseEffects.hasTachypnea(player)) {
            TachypneaLungOverlay.render(event.getGuiGraphics(),
                    event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight());
        }
    }

    @SubscribeEvent
    public static void injectIconTooltipRows(RenderTooltipEvent.GatherComponents event) {
        List<Either<FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> elements =
                event.getTooltipElements();
        for (int i = 0; i < elements.size(); i++) {
            final int idx = i;
            elements.get(i).left().ifPresent(text -> {
                if (text instanceof Component component) {
                    IconTextTooltipComponent iconRow = IconTextTooltipRenderer.fromLine(component);
                    if (iconRow != null) {
                        elements.set(idx, Either.right(iconRow));
                    }
                }
            });
        }
    }
}
