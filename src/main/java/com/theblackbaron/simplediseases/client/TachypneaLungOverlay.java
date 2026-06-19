package com.theblackbaron.simplediseases.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a lung-themed air bar while tachypnea is active, including on land.
 * Position matches vanilla air icons (one row below the food bar).
 */
public final class TachypneaLungOverlay {
    private static final ResourceLocation LUNGS_TEXTURE =
            new ResourceLocation(SimpleDiseases.MOD_ID, "textures/gui/lungs.png");
    private static final int FOOD_ROW_SHIFT = 10;

    private TachypneaLungOverlay() {}

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || !DiseaseEffects.hasTachypnea(player)) return;

        int air = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        int left = screenWidth / 2 + 91;
        int top = screenHeight - 39;
        int tickCount = player.tickCount;

        RenderSystem.enableBlend();
        for (int i = 0; i < 10; i++) {
            int lungY = top;
            if (i == 9 && air < maxAir && tickCount % 2 == 0) {
                lungY--;
            }

            int u;
            if (i * 2 + 1 < air) {
                u = 16;
            } else if (i * 2 + 1 == air) {
                u = 25;
            } else {
                u = 34;
            }

            graphics.blit(LUNGS_TEXTURE, left - i * 8 - 9, lungY, u, 18, 9, 9);
        }
        RenderSystem.disableBlend();
    }

    static int foodRowShift() {
        return FOOD_ROW_SHIFT;
    }
}
