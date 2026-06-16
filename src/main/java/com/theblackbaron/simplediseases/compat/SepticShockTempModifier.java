package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.ArmorInsulationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.function.Function;

/**
 * Cold Sweat modifier for septic shock: a BASE-trait hypothermia floor (eased by nearby heat) plus
 * pre-armor RATE vasodilation. Post-armor rewarming is handled by {@link SepticShockRewarmModifier}.
 */
public class SepticShockTempModifier extends TempModifier {

    private static final double INSULATION_WARM_FACTOR = 0.03;

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait) {
        double baseOffset = maxShockOffset(entity);
        if (baseOffset <= 0.0) return temp -> temp;

        return switch (trait) {
            case BASE -> temp -> temp - effectiveBaseOffset(entity, baseOffset);
            case RATE -> rate -> {
                if (rate < 0) return rate * DiseaseMobEffect.SEPTIC_SHOCK_COOL_MULT;
                if (rate > 0) {
                    double core = Temperature.get(entity, Temperature.Trait.CORE);
                    if (core < 0) {
                        double coldInsul = readColdInsulation(entity);
                        return rate * DiseaseMobEffect.SEPTIC_SHOCK_WARM_BOOST
                                * (1.0 + coldInsul * INSULATION_WARM_FACTOR);
                    }
                }
                return rate;
            };
            default -> temp -> temp;
        };
    }

    /** BASE floor minus relief from elevated WORLD heat (campfire, lava, etc.). */
    static double effectiveBaseOffset(LivingEntity entity, double baseOffset) {
        double world = Temperature.get(entity, Temperature.Trait.WORLD);
        double heatAbove = Math.max(0.0, world - DiseaseMobEffect.SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD);
        double relief = heatAbove * DiseaseMobEffect.SEPTIC_SHOCK_HEAT_RELIEF_SCALE;
        double maxRelief = baseOffset * DiseaseMobEffect.SEPTIC_SHOCK_MAX_HEAT_RELIEF_FRAC;
        return Math.max(0.0, baseOffset - Math.min(relief, maxRelief));
    }

    static double readColdInsulation(LivingEntity entity) {
        double total = 0.0;
        for (TempModifier mod : Temperature.getModifiers(entity, Temperature.Trait.RATE)) {
            if (mod instanceof ArmorInsulationTempModifier) {
                total += mod.getNBT().getDouble("cold");
            }
        }
        return total;
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
