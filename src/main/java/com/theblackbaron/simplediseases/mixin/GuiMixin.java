package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow @Final private RandomSource random;

    @Unique private int simplediseases$pendingHeartX;
    @Unique private int simplediseases$shakeAnchorX = Integer.MIN_VALUE;
    @Unique private int simplediseases$shakeAnchorY = Integer.MIN_VALUE;
    @Unique private int simplediseases$shakeDelta;

    @ModifyVariable(
            method = "renderHeart",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private int simplediseases$trackHeartX(int x) {
        this.simplediseases$pendingHeartX = x;
        return x;
    }

    /** One Y offset per heart slot so container outline and fill shake together. */
    @ModifyVariable(
            method = "renderHeart",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 1)
    private int simplediseases$heartShakeY(int y) {
        var player = Minecraft.getInstance().player;
        if (player == null || !DiseaseEffects.hasTachycardia(player)) {
            return y;
        }
        if (this.simplediseases$pendingHeartX != this.simplediseases$shakeAnchorX
                || y != this.simplediseases$shakeAnchorY) {
            this.simplediseases$shakeAnchorX = this.simplediseases$pendingHeartX;
            this.simplediseases$shakeAnchorY = y;
            this.simplediseases$shakeDelta = this.random.nextInt(2);
        }
        return y + this.simplediseases$shakeDelta;
    }
}
