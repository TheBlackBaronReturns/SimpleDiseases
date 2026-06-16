package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.mehvahdjukaar.jeed.common.EffectRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.effect.MobEffect;
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
    private static void injectFeverTooltip(
            MobEffectInstance instance, TooltipFlag flag, boolean a, boolean b,
            CallbackInfoReturnable<List<Component>> cir) {
        MobEffect effect = instance.getEffect();
        if (!(effect instanceof DiseaseMobEffect dme)) return;
        double feverOffset = dme.getFeverOffset();
        if (feverOffset <= 0.0) return;

        String langKey = feverOffset >= DiseaseMobEffect.FEVER_SEVERE ? "simplediseases.fever.severe" :
                         feverOffset >= DiseaseMobEffect.FEVER_HIGH   ? "simplediseases.fever.high" :
                         feverOffset >= DiseaseMobEffect.FEVER_MILD   ? "simplediseases.fever.mild" :
                                                                        "simplediseases.fever.light";

        List<Component> list = cir.getReturnValue();
        int headerIdx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof TranslatableContents tc
                    && "potion.whenDrank".equals(tc.getKey())) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) return;

        list.add(headerIdx + 1, Component.translatable(langKey));
    }

    @Inject(method = "getTooltipsWithDescription", at = @At("RETURN"), require = 0)
    private static void injectShockTooltip(
            MobEffectInstance instance, TooltipFlag flag, boolean a, boolean b,
            CallbackInfoReturnable<List<Component>> cir) {
        MobEffect effect = instance.getEffect();
        if (!(effect instanceof DiseaseMobEffect dme)) return;
        if (dme.getShockOffset() <= 0.0) return;

        List<Component> list = cir.getReturnValue();
        int headerIdx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getContents() instanceof TranslatableContents tc
                    && "potion.whenDrank".equals(tc.getKey())) {
                headerIdx = i;
                break;
            }
        }
        if (headerIdx < 0) return;
        list.add(headerIdx + 1, Component.translatable("simplediseases.shock"));
    }
}
