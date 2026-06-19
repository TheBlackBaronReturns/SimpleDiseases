package com.theblackbaron.simplediseases.mixin;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "causeFoodExhaustion", at = @At("RETURN"))
    private void simplediseases$doubleSprintExhaustion(float amount, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (self.isSprinting() && DiseaseEffects.hasTachycardia(self)) {
            self.getFoodData().addExhaustion(amount);
        }
    }
}
