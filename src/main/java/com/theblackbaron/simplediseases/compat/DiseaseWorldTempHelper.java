package com.theblackbaron.simplediseases.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.ArmorInsulationTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Shared disease WORLD-temperature math for recovery gates and CS perception modifiers.
 */
public final class DiseaseWorldTempHelper {

    private DiseaseWorldTempHelper() {}

    /** Net WORLD offset from active disease fever (+) and septic shock (−). */
    public static double perceptionOffset(LivingEntity entity) {
        double fever = FeverWorldTempModifier.maxFeverOffset(entity);
        if (fever <= 0.0 && maxShockOffset(entity) <= 0.0) return 0.0;
        double modified = Temperature.get(entity, Temperature.Trait.WORLD);
        double rawApprox = modified - fever;
        double shock = effectiveShockPenalty(entity, rawApprox);
        return fever - shock;
    }

    /** Shock WORLD penalty magnitude after heat relief from {@code rawWorld}. */
    public static double effectiveShockPenalty(LivingEntity entity, double rawWorld) {
        double baseOffset = maxShockOffset(entity);
        if (baseOffset <= 0.0) return 0.0;
        double heatAbove = Math.max(0.0, rawWorld - DiseaseMobEffect.SEPTIC_SHOCK_HEAT_RELIEF_THRESHOLD);
        double relief = heatAbove * DiseaseMobEffect.SEPTIC_SHOCK_HEAT_RELIEF_SCALE;
        double maxRelief = baseOffset * DiseaseMobEffect.SEPTIC_SHOCK_MAX_HEAT_RELIEF_FRAC;
        return Math.max(0.0, baseOffset - Math.min(relief, maxRelief));
    }

    public static double maxShockOffset(LivingEntity entity) {
        double max = 0.0;
        for (MobEffectInstance inst : entity.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme) {
                double offset = dme.getShockOffset();
                if (offset > max) max = offset;
            }
        }
        return max;
    }

    /** Sum of hot insulation from worn Cold Sweat armor (RATE-trait modifiers). */
    public static double readHotInsulation(LivingEntity entity) {
        double total = 0.0;
        for (TempModifier mod : Temperature.getModifiers(entity, Temperature.Trait.RATE)) {
            if (mod instanceof ArmorInsulationTempModifier) {
                total += mod.getNBT().getDouble("hot");
            }
        }
        return total;
    }
}
