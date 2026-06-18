package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectTextureManager.class)
public abstract class MobEffectTextureManagerMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void simplediseases$sharedIcon(MobEffect effect, CallbackInfoReturnable<TextureAtlasSprite> cir) {
        if (effect instanceof DiseaseMobEffect dme) {
            ResourceLocation iconId = dme.getSharedIconId();
            if (iconId != null) {
                cir.setReturnValue(((TextureAtlasHolderInvoker) (Object) this).simplediseases$invokeGetSprite(iconId));
            }
        }
    }
}
