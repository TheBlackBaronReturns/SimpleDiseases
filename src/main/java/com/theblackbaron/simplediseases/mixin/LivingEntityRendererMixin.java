package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity> {

    /**
     * Reuses vanilla powdered-snow body shake ({@code setupRotations} yaw wobble) for disease fever
     * and septic shock, instead of a custom pose-stack transform.
     */
    @Inject(method = "isShaking", at = @At("RETURN"), cancellable = true)
    private void simplediseases$isShaking(T entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()
                && (DiseaseEffects.hasShiveringDisease(entity) || DiseaseEffects.hasSepticShock(entity))) {
            cir.setReturnValue(true);
        }
    }
}
