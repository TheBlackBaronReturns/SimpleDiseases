package com.theblackbaron.simplediseases.status.category;

import com.mojang.serialization.MapCodec;
import com.theblackbaron.simplediseases.compat.ColdSweatCompat;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.component.ComponentType;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.component.TierComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.WorseningRoll;
import com.theblackbaron.simplediseases.status.manager.ImmuneManager;
import com.theblackbaron.simplediseases.status.service.SymptomService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Set;

/**
 * Bacterial archetype: wound-seeded symptomatic colonization that accumulates while a flesh wound
 * is open, decays without one, latches once critical mass is reached, then stochastically worsens
 * through severity tiers. Once progress reaches {@code progressCap}, the infection begins natural
 * recovery at {@code recoveryRate}; if it drains to zero the player is cured.
 *
 * <p>Triggered complications (e.g. sepsis from cellulitis) are handled entirely by
 * {@link ComplicationCategory} with a {@code triggeredBy} field on their definition. This category
 * is now purely wound-seeded.
 *
 * <p>Episode suppression: once any complication child of this disease passes its first symptom
 * threshold, this disease's own episodes are suppressed via {@link DiseaseContext#suppressEpisodes}.
 */
public final class BacterialCategory implements DiseaseCategory {

    private static final int CAP_RECOVERY_BIT = 1 << 30;

    public static final ResourceLocation ID = new ResourceLocation(SimpleDiseases.MOD_ID, "bacterial");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public MapCodec<? extends DiseaseDef> defCodec() {
        return BacterialDiseaseDef.CODEC;
    }

    @Override
    public Set<ComponentType<?>> componentTypes() {
        return Set.of(Components.PROGRESS, Components.SYMPTOMS, Components.TIER);
    }

    @Override
    public boolean contagious() {
        return false;
    }

    @Override
    public void tick(DiseaseDef def, DiseaseInstance instance, DiseaseContext ctx) {
        BacterialDiseaseDef  bdef     = (BacterialDiseaseDef) def;
        ServerPlayer         player   = ctx.player();
        long                 gameTime = ctx.gameTime();
        ProgressComponent    prog     = instance.get(Components.PROGRESS);
        SymptomPoolComponent pool     = instance.get(Components.SYMPTOMS);
        TierComponent        tier     = instance.get(Components.TIER);

        if (!prog.inRecovery) {
            tickColonization(bdef, prog, pool, tier, player, ctx);
        } else {
            tickActiveInfection(bdef, prog, pool, tier, player, gameTime, ctx);
        }
    }

    private static void tickColonization(BacterialDiseaseDef bdef, ProgressComponent prog,
                                          SymptomPoolComponent pool, TierComponent tier,
                                          ServerPlayer player, DiseaseContext ctx) {
        // Accumulate while a flesh wound is open; decay without one.
        boolean accumulate = ctx.state().injury().fleshWoundSeverity() >= 0;
        if (accumulate) {
            prog.add(bdef.accumulationRate(), bdef.latchThreshold());
        } else {
            prog.add(-bdef.recoveryRate(), bdef.latchThreshold());
        }

        if (prog.progress <= 0.0) {
            resetIllness(player, bdef, pool, tier);
            return;
        }

        // Roll severity at the first symptom threshold.
        double firstThreshold = bdef.symptoms().thresholds().isEmpty()
                ? bdef.latchThreshold() : bdef.symptoms().thresholds().get(0);
        if (prog.progress >= firstThreshold && !tier.rolled()) {
            rollSeverity(bdef, tier, player);
        }

        // Reveal symptoms progressively; use MODERATE as a placeholder until the tier is rolled.
        Severity sev = tier.rolled() ? tier.severity() : Severity.MODERATE;
        MobEffect diseaseEff = tier.rolled() ? bdef.effectFor(sev).get() : null;
        SymptomService.syncPool(player, pool, bdef.symptoms(), prog.progress, sev, diseaseEff);

        if (prog.progress >= bdef.latchThreshold()) {
            prog.inRecovery = true;
            if (!tier.rolled()) rollSeverity(bdef, tier, player);
            ctx.state().injury().clearFleshWound();
            Severity severity = tier.severity();
            player.addEffect(new MobEffectInstance(bdef.effectFor(severity).get(), -1, 0, false, false, true));
            player.sendSystemMessage(Component.translatable(bdef.caughtKey()));
        }
    }

    private static void tickActiveInfection(BacterialDiseaseDef bdef, ProgressComponent prog,
                                             SymptomPoolComponent pool, TierComponent tier,
                                             ServerPlayer player, long gameTime, DiseaseContext ctx) {
        boolean inCapRecovery = (tier.worseningChecks & CAP_RECOVERY_BIT) != 0;

        if (!inCapRecovery && prog.progress >= bdef.progressCap()) {
            tier.worseningChecks |= CAP_RECOVERY_BIT;
            inCapRecovery = true;
        }

        if (inCapRecovery) {
            double mult = ctx.recoveryMultiplier(bdef.pathogenType());
            if (mult > 0.0) {
                prog.add(-bdef.recoveryRate() * mult, bdef.progressCap());
            }
            if (prog.progress <= 0.0) {
                prog.inRecovery = false;
                player.sendSystemMessage(Component.translatable(bdef.curedKey()));
                resetIllness(player, bdef, pool, tier);
                return;
            }
        } else {
            prog.add(bdef.worseningRate(), bdef.progressCap());
        }

        // Defensive: latched without a roll (e.g. migrated save with severity=-1).
        if (!tier.rolled()) rollSeverity(bdef, tier, player);

        // Swap to the current tier's permanent effect.
        Severity severity = tier.severity();
        if (!inCapRecovery) {
            tickSeverityWorsening(bdef, tier, prog.progress, player, pool, bdef.effectFor(severity).get());
        }
        for (Severity other : bdef.tiers()) {
            if (other != severity) {
                MobEffect stale = bdef.effectFor(other).get();
                if (player.hasEffect(stale)) player.removeEffect(stale);
            }
        }
        if (!player.hasEffect(bdef.effectFor(severity).get())) {
            player.addEffect(new MobEffectInstance(bdef.effectFor(severity).get(), -1, 0, false, false, true));
        }
        if (bdef.id().equals(DiseaseRegistry.SEPSIS_STAPH)) {
            ColdSweatCompat.syncDiseaseWorldModifiers(player);
        }

        // Suppress episodes when a complication child has passed its first threshold.
        if (!ctx.suppressEpisodes(bdef.id())) {
            MobEffect diseaseEff = bdef.effectFor(severity).get();
            SymptomService.tickEpisodes(player, pool, bdef.symptoms(), gameTime, severity, diseaseEff);
        }
    }

    private static void tickSeverityWorsening(BacterialDiseaseDef bdef, TierComponent tier,
                                               double progress, ServerPlayer player,
                                               SymptomPoolComponent pool, MobEffect diseaseEff) {
        if (!tier.rolled()) { tier.previousWorseningProgress = progress; return; }
        List<Double> thresholds   = bdef.worseningThresholds();
        int          maxSevOrdinal = bdef.tiers().get(bdef.tierCount() - 1).ordinal();
        for (int i = 0; i < thresholds.size(); i++) {
            int    bit       = 1 << i;
            double threshold = thresholds.get(i);
            if ((tier.worseningChecks & bit) != 0
                    || tier.previousWorseningProgress >= threshold
                    || progress < threshold) continue;
            tier.worseningChecks |= bit;
            if (tier.severity >= maxSevOrdinal) continue;
            float chance = WorseningRoll.chance(tier.worsenings);
            if (player.getRandom().nextFloat() < chance) {
                Severity oldTier = Severity.byOrdinal(tier.severity);
                tier.severity++;
                tier.worsenings++;
                SymptomService.tryUpgradeIncidental(player, pool, bdef.symptoms(), oldTier,
                        Severity.byOrdinal(tier.severity), diseaseEff);
                player.sendSystemMessage(Component.translatable(bdef.worsensKey()));
            }
        }
        tier.previousWorseningProgress = progress;
    }

    private static void rollSeverity(BacterialDiseaseDef bdef, TierComponent tier, ServerPlayer player) {
        int rolls = ImmuneManager.getSeverityRolls(player);
        tier.severity  = Severity.rollWeightedBiased(bdef.tiers(), player.getRandom(), rolls).ordinal();
        tier.reductions = 0;
    }

    private static void resetIllness(ServerPlayer player, BacterialDiseaseDef bdef,
                                      SymptomPoolComponent pool, TierComponent tier) {
        bdef.removeEffects(player);
        SymptomService.clearActive(player, pool, bdef.symptoms());
        pool.clearAll();
        pool.nextEpisodeAt             = 0L;
        tier.severity                  = -1;
        tier.reductions                = 0;
        tier.worseningChecks           = 0;
        tier.worsenings                = 0;
        tier.previousWorseningProgress = 0.0;
    }
}
