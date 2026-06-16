package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Lowers perceived ambient (WORLD) temperature during septic shock, eased by nearby heat.
 * Does not affect BODY/BASE/RATE — only shifts how Cold Sweat reads the environment.
 */
public class SepticShockTempModifier extends TempModifier {

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        if (trait != Temperature.Trait.WORLD) return temp -> temp;
        double fever = FeverWorldTempModifier.maxFeverOffset(entity);
        return temp -> {
            double rawWorld = temp - fever;
            double penalty = DiseaseWorldTempHelper.effectiveShockPenalty(entity, rawWorld);
            if (penalty <= 0.0) return temp;
            return temp - penalty;
        };
    }
}
