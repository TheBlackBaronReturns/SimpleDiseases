package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Shadow @Final private RandomSource random;

    /** Per-heart Y jitter matching vanilla low-health heart shake. */
    @ModifyVariable(
            method = "renderHeart",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 3)
    private int simplediseases$heartShakeY(int y) {
        var player = Minecraft.getInstance().player;
        if (player != null && DiseaseEffects.hasTachycardia(player)) {
            return y + this.random.nextInt(2);
        }
        return y;
    }
}
