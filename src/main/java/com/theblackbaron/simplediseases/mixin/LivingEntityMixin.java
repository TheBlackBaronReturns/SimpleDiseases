package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /**
     * After vanilla air handling: double underwater drain while sprinting on land, recover when not sprinting.
     */
    @Inject(method = "baseTick", at = @At("TAIL"))
    private void simplediseases$tachypneaAir(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof ServerPlayer player)) return;
        if (player.level().isClientSide()) return;
        if (!DiseaseEffects.hasTachypnea(player)) return;

        boolean inWater = player.isEyeInFluid(FluidTags.WATER);
        if (inWater) {
            player.setAirSupply(Math.max(-20, player.getAirSupply() - 1));
        } else if (player.isSprinting()) {
            player.setAirSupply(Math.max(-20, player.getAirSupply() - 6));
        } else if (player.getAirSupply() < player.getMaxAirSupply()) {
            player.setAirSupply(Math.min(player.getMaxAirSupply(), player.getAirSupply() + 4));
        }

        if (!inWater && player.getAirSupply() <= 0 && player.tickCount % 20 == 0) {
            player.hurt(player.damageSources().drown(), 2.0F);
        }
    }
}
