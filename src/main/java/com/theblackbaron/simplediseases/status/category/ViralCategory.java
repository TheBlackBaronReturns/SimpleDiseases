package com.theblackbaron.simplediseases.status.category;

import com.mojang.serialization.MapCodec;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.component.ComponentType;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.component.TierComponent;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.SymptomAction;
import com.theblackbaron.simplediseases.status.def.SymptomEntry;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.def.WorseningRoll;
import com.theblackbaron.simplediseases.status.manager.ImmuneManager;
import com.theblackbaron.simplediseases.status.service.SymptomService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Set;

/**
 * Viral archetype: contagious, environmental accumulation that latches at a threshold and recovers
 * over time with timed immunity and tier-scaled symptom episodes. Severity is rolled once at the first
 * symptom threshold (weighted toward mild) and fixed for the illness; it selects the named effect
 * variant, gates which symptoms can enter the pool, and scales symptom pacing.
 */
public final class ViralCategory implements DiseaseCategory {

    public static final ResourceLocation ID = new ResourceLocation(SimpleDiseases.MOD_ID, "viral");
    private static final double[] THREE_TIER_CAP_2_WORSENING_THRESHOLDS  = {1.5, 2.0};
    private static final double[] THREE_TIER_CAP_10_WORSENING_THRESHOLDS = {4.0, 7.0};
    private static final double[] FOUR_TIER_CAP_10_WORSENING_THRESHOLDS  = {3.0, 6.0, 8.0};
    private static final double[] NO_WORSENING_THRESHOLDS = {};

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public MapCodec<? extends DiseaseDef> defCodec() {
        return ViralDiseaseDef.CODEC;
    }

    @Override
    public Set<ComponentType<?>> componentTypes() {
        return Set.of(Components.PROGRESS, Components.IMMUNITY, Components.SYMPTOMS, Components.TIER);
    }

    @Override
    public boolean contagious() {
        return true;
    }

    @Override
    public void tick(DiseaseDef def, DiseaseInstance instance, DiseaseContext ctx) {
        ViralDiseaseDef      vdef     = (ViralDiseaseDef) def;
        ServerPlayer         player   = ctx.player();
        long                 gameTime = ctx.gameTime();
        ProgressComponent    prog     = instance.get(Components.PROGRESS);
        SymptomPoolComponent pool     = instance.get(Components.SYMPTOMS);
        TierComponent        tier     = instance.get(Components.TIER);

        // Passive recovery — scaled by warmth/fever; zero while damp/wind adds progress this tick.
        double mult = ctx.recoveryMultiplier(vdef.pathogenType());
        if (mult > 0.0) {
            prog.add(-vdef.recoveryRate() * mult, vdef.progressCap());
        }

        // Roll severity once, the moment accumulation reaches the first symptom threshold — early
        // enough that the rolled tier can gate which symptoms are eligible to enter the pool (the pool
        // is filled at thresholds during accumulation, before the latch). Fixed for the illness after.
        double firstThreshold = vdef.symptoms().thresholds().isEmpty()
                ? vdef.latchThreshold() : vdef.symptoms().thresholds().get(0);
        if (prog.progress >= firstThreshold && !tier.rolled()) {
            rollSeverity(vdef, tier, player);
        }

        // Latch at the full threshold; cure when progress drains to 0 during recovery; reset the
        // rolled tier (and any early pool) if accumulation fizzles back to 0 before ever latching.
        if (prog.progress >= vdef.latchThreshold()) {
            if (!prog.inRecovery) {
                player.sendSystemMessage(Component.translatable(vdef.caughtKey()));
            }
            prog.inRecovery = true;
        } else if (prog.inRecovery && prog.progress <= 0.0) {
            prog.inRecovery = false;
            ctx.state().grantGroupImmunity(vdef.exclusionGroup(), gameTime, DiseaseRegistry.VIRAL_IMMUNITY_TICKS);
            player.sendSystemMessage(Component.translatable(vdef.curedKey()));
            resetIllness(player, vdef, pool, tier);
        } else if (!prog.inRecovery && prog.progress <= 0.0 && tier.rolled()) {
            resetIllness(player, vdef, pool, tier);
        }

        // Defensive: latched but unrolled (e.g. a save migrated from before tiers existed).
        if (prog.inRecovery && !tier.rolled()) {
            rollSeverity(vdef, tier, player);
        }

        // Disease MobEffect: while latched, keep exactly the current tier's variant present (swapping
        // if a treatment reduction changed the tier); otherwise remove any variant.
        if (prog.inRecovery) {
            Severity  severity = tier.severity();
            MobEffect want     = vdef.effectFor(severity).get();
            for (Severity other : vdef.tiers()) {
                if (other != severity) {
                    MobEffect stale = vdef.effectFor(other).get();
                    if (player.hasEffect(stale)) player.removeEffect(stale);
                }
            }
            if (!player.hasEffect(want)) {
                player.addEffect(new MobEffectInstance(want, -1, 0, false, false, true));
            }
        }
        // No `else { removeEffects }`: a variant is only ever present while latched, and it's removed
        // on the recovery-exit transition (resetIllness). Skipping it here spares healthy/accumulating
        // players a per-tick effect-resolution sweep.

        Severity sev = tier.rolled() ? tier.severity() : Severity.MODERATE;
        MobEffect diseaseEff = tier.rolled() ? vdef.effectFor(sev).get() : null;
        tickSeverityWorsening(vdef, tier, prog.progress, player, pool, diseaseEff);
        SymptomEntry fired;
        if (prog.inRecovery) {
            // Skip episodes while a complication child has passed its first symptom threshold.
            fired = ctx.suppressEpisodes(vdef.id())
                    ? null
                    : SymptomService.tickEpisodes(player, pool, vdef.symptoms(), gameTime, sev, diseaseEff);
        } else {
            fired = SymptomService.syncPool(player, pool, vdef.symptoms(), prog.progress, sev, diseaseEff);
        }

        // A vomit/diarrhea episode leaves a lingering contagious puddle (norovirus's transmission vector)
        // — but only during the recovery phase (latched). Pre-latch accumulation threshold fires drain
        // food/play SFX but do NOT place puddles. The manager filters to norovirus, so this stays
        // disease-agnostic here.
        if (prog.inRecovery && fired != null && fired.action() == SymptomAction.DRAIN_FOOD) {
            ctx.lingering().onPlayerVomit(player, vdef);
        }
    }

    /** Clears the disease's tier variant, the rolled tier, the symptom pool, and any active symptom
     *  effects for a finished illness (cure-from-latch or a pre-latch accumulation fizzle). */
    private static void resetIllness(ServerPlayer player, ViralDiseaseDef vdef,
                                     SymptomPoolComponent pool, TierComponent tier) {
        vdef.removeEffects(player);
        SymptomService.clearActive(player, pool, vdef.symptoms());
        pool.clearAll();
        pool.nextEpisodeAt = 0L;
        tier.severity        = -1;
        tier.reductions      = 0;
        tier.worseningChecks = 0;
        tier.worsenings      = 0;
        tier.previousWorseningProgress = 0.0;
    }

    /** Weighted tier pick (toward mild), skewed toward worse tiers if the player is immunodeficient
     *  — {@link ImmuneManager#getSeverityRolls} rolls extra times and keeps the most severe. */
    private static void rollSeverity(ViralDiseaseDef def, TierComponent tier, ServerPlayer player) {
        int rolls = ImmuneManager.getSeverityRolls(player);
        tier.severity   = Severity.rollWeightedBiased(def.tiers(), player.getRandom(), rolls).ordinal();
        tier.reductions = 0;
    }

    private static void tickSeverityWorsening(ViralDiseaseDef def, TierComponent tier, double progress,
                                               ServerPlayer player, SymptomPoolComponent pool, MobEffect diseaseEff) {
        if (!tier.rolled()) {
            tier.previousWorseningProgress = progress;
            return;
        }
        double[] thresholds = worseningThresholds(def);
        int maxSeverity = maxSeverityOrdinal(def);
        for (int i = 0; i < thresholds.length; i++) {
            int bit = 1 << i;
            double threshold = thresholds[i];
            if ((tier.worseningChecks & bit) != 0
                    || tier.previousWorseningProgress >= threshold
                    || progress < threshold) continue;
            tier.worseningChecks |= bit;
            if (tier.severity >= maxSeverity) continue;
            float chance = WorseningRoll.chance(tier.worsenings);
            if (player.getRandom().nextFloat() < chance) {
                Severity oldTier = Severity.byOrdinal(tier.severity);
                tier.severity++;
                tier.worsenings++;
                SymptomService.tryUpgradeIncidental(player, pool, def.symptoms(), oldTier,
                        Severity.byOrdinal(tier.severity), diseaseEff);
                player.sendSystemMessage(Component.literal("§cYour condition worsens."));
            }
        }
        tier.previousWorseningProgress = progress;
    }

    private static double[] worseningThresholds(ViralDiseaseDef def) {
        int tiers = def.tierCount();
        double cap = def.progressCap();
        if (tiers == 3 && cap <= 2.0) return THREE_TIER_CAP_2_WORSENING_THRESHOLDS;
        if (tiers == 3 && cap <= 10.0) return THREE_TIER_CAP_10_WORSENING_THRESHOLDS;
        if (tiers == 4 && cap <= 10.0) return FOUR_TIER_CAP_10_WORSENING_THRESHOLDS;
        return NO_WORSENING_THRESHOLDS;
    }

    private static int maxSeverityOrdinal(ViralDiseaseDef def) {
        return Math.min(Severity.DEBILITATING.ordinal(), Severity.MODERATE.ordinal() + def.tierCount() / 2);
    }
}
