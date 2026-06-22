package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.client.tooltip.DiseaseTooltipHelper;
import net.mehvahdjukaar.jeed.common.EffectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = EffectRenderer.class, remap = false)
public abstract class EffectRendererMixin {

    @Inject(method = "getTooltipsWithDescription", at = @At("RETURN"), require = 0)
    private static void injectDiseaseTooltipRows(
            MobEffectInstance instance, TooltipFlag flag, boolean a, boolean b,
            CallbackInfoReturnable<List<Component>> cir) {
        if (!DiseaseTooltipHelper.isDiseaseTierEffect(instance.getEffect())) return;

        DiseaseTooltipHelper.applyDiseaseTooltip(
                cir.getReturnValue(), instance, Minecraft.getInstance().player);
    }
}
