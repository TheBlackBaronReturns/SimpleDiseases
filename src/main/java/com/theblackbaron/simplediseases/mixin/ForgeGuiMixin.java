package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ForgeGui.class, remap = false)
public abstract class ForgeGuiMixin {

    @Inject(method = "renderFood", at = @At("HEAD"), remap = false)
    private void simplediseases$stomachCrampsFoodTint(int width, int height, GuiGraphics guiGraphics, CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && DiseaseEffects.hasStomachCramps(player)) {
            // Warm multiplier — tints icons without washing them to flat red.
            guiGraphics.setColor(1.0F, 0.82F, 0.78F, 1.0F);
        }
    }

    @Inject(method = "renderFood", at = @At("RETURN"), remap = false)
    private void simplediseases$resetFoodTint(GuiGraphics guiGraphics, CallbackInfo ci) {
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
