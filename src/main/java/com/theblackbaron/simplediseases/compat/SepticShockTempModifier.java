package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Cold Sweat BASE-trait penalty for septic shock. Subtracts the active shock offset from the
 * computed BASE temperature so CORE thermodynamics (environment, insulation, heat sources) still
 * move BODY freely.
 */
public class SepticShockTempModifier extends TempModifier {

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        double offset = maxShockOffset(entity);
        if (offset <= 0.0) return temp -> temp;
        return temp -> temp - offset;
    }

    static double maxShockOffset(LivingEntity entity) {
        double max = 0.0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme) {
                double offset = dme.getShockOffset();
                if (offset > max) max = offset;
            }
        }
        return max;
    }
}
