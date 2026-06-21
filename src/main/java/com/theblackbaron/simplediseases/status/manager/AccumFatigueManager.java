package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Anti-exploit: prolonged damp/wind re-exposure while latched on a curable disease applies
 * {@link DiseaseEffects#IMMUNE_DEFICIENCY}. Streak decays gradually when dry, not instant reset.
 */
public final class AccumFatigueManager {

    public static final long ACCUM_FATIGUE_WARN_TICKS    = 8L * 60 * 20;
    public static final long ACCUM_FATIGUE_PENALTY_TICKS = 15L * 60 * 20;
    public static final long ACCUM_FATIGUE_DECAY_PER_TICK = 1L;

    private AccumFatigueManager() {}

    public static void tick(ServerPlayer player, PlayerDiseaseState state,
                            boolean viralEnvAccumulating) {
        boolean latchedCurable = hasLatchedCurableDisease(state);
        long streak = state.getAccumFatigueStreakTicks();

        if (!latchedCurable) {
            state.setAccumFatigueStreakTicks(0);
            state.setAccumFatigueWarned(false);
            if (state.isFatigueDeficiencyActive()) {
                player.removeEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get());
                state.setFatigueDeficiencyActive(false);
            }
            return;
        }

        if (viralEnvAccumulating) {
            streak = Math.min(ACCUM_FATIGUE_PENALTY_TICKS, streak + 1);
            state.setAccumFatigueStreakTicks(streak);
        } else if (streak > 0) {
            streak = Math.max(0, streak - ACCUM_FATIGUE_DECAY_PER_TICK);
            state.setAccumFatigueStreakTicks(streak);
        }

        if (streak < ACCUM_FATIGUE_WARN_TICKS) {
            state.setAccumFatigueWarned(false);
        }

        if (streak >= ACCUM_FATIGUE_WARN_TICKS && !state.isAccumFatigueWarned()) {
            player.displayClientMessage(
                    Component.translatable("message.simplediseases.accum_fatigue_warn"), true);
            state.setAccumFatigueWarned(true);
        }

        if (streak >= ACCUM_FATIGUE_PENALTY_TICKS
                && !state.isFatigueDeficiencyActive()
                && !player.hasEffect(DiseaseEffects.IMMUNE_DEFICIENCY.get())) {
            player.addEffect(new MobEffectInstance(
                    DiseaseEffects.IMMUNE_DEFICIENCY.get(), -1, 0, false, false, true));
            state.setFatigueDeficiencyActive(true);
            player.displayClientMessage(
                    Component.translatable("message.simplediseases.accum_fatigue"), true);
        }
    }

    /** Latched curable diseases — excludes lethal sepsis / MOF. */
    public static boolean hasLatchedCurableDisease(PlayerDiseaseState state) {
        for (DiseaseDef def : DiseaseRegistry.all()) {
            ResourceLocation id = def.id();
            if (DiseaseRegistry.SEPSIS_STAPH.equals(id) || DiseaseRegistry.MOF_STAPH.equals(id)) continue;
            if (state.inRecovery(id)) return true;
        }
        return false;
    }
}
