package com.theblackbaron.simplediseases.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/**
 * Draws the vanilla air bubble row when tachypnea is active on land at full air
 * (Forge only renders that row underwater or when air is below max).
 */
public final class TachypneaAirOverlay {
    private static final ResourceLocation GUI_ICONS =
            new ResourceLocation("minecraft", "textures/gui/icons.png");

    private TachypneaAirOverlay() {}

    public static void renderFullAir(GuiGraphics graphics, ForgeGui gui, int screenWidth, int screenHeight, LocalPlayer player) {
        int air = player.getAirSupply();
        int maxAir = player.getMaxAirSupply();
        int left = screenWidth / 2 + 91;
        int top = screenHeight - gui.rightHeight;

        int full = Mth.ceil((double) (air - 2) * 10.0D / (double) maxAir);
        int partial = Mth.ceil((double) air * 10.0D / (double) maxAir) - full;

        RenderSystem.enableBlend();
        for (int i = 0; i < full + partial; ++i) {
            graphics.blit(GUI_ICONS, left - i * 8 - 9, top, (i < full ? 16 : 25), 18, 9, 9);
        }
        RenderSystem.disableBlend();
    }
}
