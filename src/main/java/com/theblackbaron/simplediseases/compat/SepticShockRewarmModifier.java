package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Post-armor RATE boost during septic shock. Cold Sweat applies hot insulation to positive rates,
 * which crushes rewarming while hypothermic; this modifier runs after {@code ArmorInsulationTempModifier}
 * so campfire heat and cold insulation visibly raise BODY.
 */
public class SepticShockRewarmModifier extends TempModifier {

    private static final double INSULATION_WARM_FACTOR = 0.04;

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        if (trait != Temperature.Trait.RATE) return rate -> rate;
        return rate -> {
            if (rate <= 0.0) return rate;
            double core = Temperature.get(entity, Temperature.Trait.CORE);
            if (core >= 0.0) return rate;
            double coldInsul = SepticShockTempModifier.readColdInsulation(entity);
            return rate * DiseaseMobEffect.SEPTIC_SHOCK_REWARM_MULT
                    * (1.0 + coldInsul * INSULATION_WARM_FACTOR);
        };
    }
}
