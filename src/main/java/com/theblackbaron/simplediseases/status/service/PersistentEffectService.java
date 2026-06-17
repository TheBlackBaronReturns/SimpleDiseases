package com.theblackbaron.simplediseases.status.service;

import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.DiseaseMobEffect;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Applies infinite malaise and unified Sharp Pain once per player tick from latched diseases and
 * open flesh wounds.
 */
public final class PersistentEffectService {
    private PersistentEffectService() {}

    public static void syncForPlayer(ServerPlayer player, PlayerDiseaseState state) {
        syncMalaise(player, state);
        syncSharpPain(player, state);
    }

    private static void syncMalaise(ServerPlayer player, PlayerDiseaseState state) {
        int bestAmp = -1;
        boolean anyLatched = false;
        for (DiseaseInstance inst : state.instances()) {
            DiseaseDef def = DiseaseRegistry.get(inst.diseaseId());
            if (def == null) continue;
            ProgressComponent prog = inst.get(Components.PROGRESS);
            if (prog == null || !prog.inRecovery) continue;
            var symptoms = symptomsOf(def);
            if (symptoms == null || !symptoms.persistentEffects().malaise()) continue;
            anyLatched = true;
            MobEffect variant = variantFor(state, def, inst.diseaseId());
            if (variant instanceof DiseaseMobEffect dme) {
                bestAmp = Math.max(bestAmp, DiseaseMobEffect.malaiseAmplifierFrom(dme));
            }
        }
        if (!anyLatched) {
            player.removeEffect(DiseaseEffects.MALAISE.get());
            return;
        }
        applyIfNeeded(player, DiseaseEffects.MALAISE.get(), Math.max(0, bestAmp));
    }

    private static void syncSharpPain(ServerPlayer player, PlayerDiseaseState state) {
        int bestAmp = -1;
        if (state.injury().fleshWoundSeverity() >= 0 && !state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)) {
            bestAmp = Math.max(bestAmp, 0);
        }
        for (DiseaseInstance inst : state.instances()) {
            DiseaseDef def = DiseaseRegistry.get(inst.diseaseId());
            if (def == null) continue;
            ProgressComponent prog = inst.get(Components.PROGRESS);
            if (prog == null || !prog.inRecovery) continue;
            var symptoms = symptomsOf(def);
            if (symptoms == null || symptoms.persistentEffects().painAmplifier().isEmpty()) continue;
            bestAmp = Math.max(bestAmp, symptoms.persistentEffects().painAmplifier().getAsInt());
        }
        if (bestAmp < 0) {
            player.removeEffect(DiseaseEffects.SHARP_PAIN.get());
            return;
        }
        applyIfNeeded(player, DiseaseEffects.SHARP_PAIN.get(), bestAmp);
    }

    private static MobEffect variantFor(PlayerDiseaseState state, DiseaseDef def, ResourceLocation id) {
        Severity sev = state.tierOf(id);
        if (sev == null) return null;
        if (def instanceof ViralDiseaseDef v) return v.effectFor(sev).get();
        if (def instanceof BacterialDiseaseDef b) return b.effectFor(sev).get();
        if (def instanceof ComplicationDiseaseDef) {
            ResourceLocation sourceId = state.complicationSource(id);
            if (sourceId != null) {
                return DiseaseEffects.complicationVariant(id, sourceId, sev).get();
            }
        }
        return null;
    }

    private static void applyIfNeeded(ServerPlayer player, MobEffect effect, int amp) {
        MobEffectInstance cur = player.getEffect(effect);
        if (cur != null && cur.getAmplifier() == amp
                && cur.getDuration() == MobEffectInstance.INFINITE_DURATION) {
            return;
        }
        if (cur != null) player.removeEffect(effect);
        player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION,
                amp, false, false, true));
    }

    private static com.theblackbaron.simplediseases.status.def.SymptomConfig symptomsOf(DiseaseDef def) {
        if (def instanceof ViralDiseaseDef v) return v.symptoms();
        if (def instanceof BacterialDiseaseDef b) return b.symptoms();
        if (def instanceof ComplicationDiseaseDef c) return c.symptoms();
        return null;
    }
}
