package com.theblackbaron.simplediseases.client;

import com.mojang.math.Axis;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-side shivering visuals for severe respiratory disease. The camera shake affects the local
 * player, and the player-render transform makes the tremble visible on other players without using
 * vanilla frozen ticks or adding movement/camera-zoom penalties.
 */
@Mod.EventBusSubscriber(modid = SimpleDiseases.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {
    private static final float SHIVER_ROLL_DEGREES  = 0.85F;
    private static final float SHIVER_PITCH_DEGREES = 0.18F;
    private static final float BODY_SHIVER_DEGREES  = 1.60F;
    private static final double BODY_SHIVER_OFFSET  = 0.012D;

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !DiseaseEffects.hasShiveringDisease(player)) return;
        double t = player.tickCount + event.getPartialTick();
        float roll = (float) (Math.sin(t * 2.70) * SHIVER_ROLL_DEGREES
                + Math.sin(t * 5.10) * SHIVER_ROLL_DEGREES * 0.35F);
        float pitch = (float) (Math.sin(t * 3.80 + 1.20) * SHIVER_PITCH_DEGREES);
        event.setRoll(event.getRoll() + roll);
        event.setPitch(event.getPitch() + pitch);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!DiseaseEffects.hasShiveringDisease(event.getEntity())) return;
        double t = event.getEntity().tickCount + event.getPartialTick();
        float roll = (float) (Math.sin(t * 2.90) * BODY_SHIVER_DEGREES);
        double x = Math.sin(t * 5.30) * BODY_SHIVER_OFFSET;
        event.getPoseStack().translate(x, 0.0D, 0.0D);
        event.getPoseStack().mulPose(Axis.ZP.rotationDegrees(roll));
    }

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
