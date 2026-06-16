package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Raises perceived ambient (WORLD) temperature while the player has an active disease fever.
 * Does not affect BODY/BASE/RATE — only shifts how Cold Sweat reads the environment.
 */
public class FeverWorldTempModifier extends TempModifier {

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        if (trait != Temperature.Trait.WORLD) return temp -> temp;
        double offset = maxFeverOffset(entity);
        if (offset <= 0.0) return temp -> temp;
        return temp -> temp + offset;
    }

    /** Maximum fever WORLD offset from active disease effects. */
    public static double maxFeverOffset(LivingEntity entity) {
        double max = 0.0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme) {
                double offset = dme.getFeverOffset();
                if (offset > max) max = offset;
            }
        }
        return max;
    }
}
