package com.theblackbaron.simplediseases.status.category;

import com.mojang.serialization.MapCodec;
import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.component.ComponentType;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.component.SourceComponent;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.component.TierComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.SymptomConfig;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.ImmuneManager;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import com.theblackbaron.simplediseases.status.service.SourceSymptomSnapshot;
import com.theblackbaron.simplediseases.status.service.SymptomService;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Unified complication archetype — handles all parent→child disease relationships. Two gate modes:
 *
 * <p><b>Viral gate</b> ({@code triggeredBy} absent): accumulates while a qualifying viral source is
 * active at the right tier. Rate = 1/randomLatchTicks × complicationMultiplier; decays at
 * {@code decayRate} when the source drops. Pre-latch symptoms revealed progressively via
 * {@link SymptomService#syncPool}. Absorbs the source on latch.
 *
 * <p><b>Bacterial gate</b> ({@code triggeredBy} present): accumulates while the named source
 * bacterial disease is latched at its {@code progressCap} and max tier. Rate = {@code
 * accumulationRate}; decays at {@code decayRate} when gate closes. Same pre-latch progressive
 * symptoms. Absorbs the source on latch.
 *
 * <p>Both gates: tier at latch is rolled fresh using a biased roll where the source disease's tier
 * determines the roll count (LIGHT/MILD → 1, MODERATE → 2, SEVERE → 3, DEBILITATING → tierCount).
 * Effects are per-source-per-tier. Worsening is either stochastic momentum (0.30 + 0.25×worsenings)
 * or deterministic threshold-crossing, chosen per-def.
 */
public final class ComplicationCategory implements DiseaseCategory {

    public static final ResourceLocation ID = new ResourceLocation(SimpleDiseases.MOD_ID, "viral_complication");

    private static final float  STOCHASTIC_BASE_CHANCE = 0.30f;
    private static final float  STOCHASTIC_MOMENTUM    = 0.25f;
    private static final double FALLBACK_RECOVERY_RATE = 0.00003;

    @Override public ResourceLocation id() { return ID; }
    @Override public MapCodec<? extends DiseaseDef> defCodec() { return ComplicationDiseaseDef.CODEC; }
    @Override public Set<ComponentType<?>> componentTypes() {
        return Set.of(Components.PROGRESS, Components.IMMUNITY, Components.SYMPTOMS, Components.SOURCE, Components.TIER);
    }
    @Override public boolean contagious() { return false; }

    @Override
    public void tick(DiseaseDef def, DiseaseInstance instance, DiseaseContext ctx) {
        ComplicationDiseaseDef cdef      = (ComplicationDiseaseDef) def;
        ServerPlayer           player    = ctx.player();
        PlayerDiseaseState     state     = ctx.state();
        long                   gameTime  = ctx.gameTime();
        ProgressComponent      prog      = instance.get(Components.PROGRESS);
        SymptomPoolComponent   pool      = instance.get(Components.SYMPTOMS);
        SourceComponent        src       = instance.get(Components.SOURCE);
        TierComponent          tier      = instance.get(Components.TIER);

        boolean recoverySuppressed  = ctx.suppressRecovery(cdef.exclusionGroup());
        boolean worseningConditions = ctx.worsensComplication(cdef.exclusionGroup());

        if (!prog.inRecovery) {
            tickPreLatch(cdef, prog, pool, src, tier, player, state, gameTime, instance);
        } else {
            tickPostLatch(cdef, prog, pool, src, tier, player, state, gameTime, instance,
                          recoverySuppressed, worseningConditions);
        }
    }

    // =========================================================================
    // PRE-LATCH
    // =========================================================================

    private void tickPreLatch(ComplicationDiseaseDef cdef, ProgressComponent prog, SymptomPoolComponent pool,
                               SourceComponent src, TierComponent tier,
                               ServerPlayer player, PlayerDiseaseState state, long gameTime,
                               DiseaseInstance instance) {
        if (cdef.triggeredBy().isPresent()) {
            tickBacterialPreLatch(cdef, prog, pool, src, tier, player, state, instance);
        } else {
            tickViralPreLatch(cdef, prog, pool, src, tier, player, state, gameTime, instance);
        }
    }

    private void tickBacterialPreLatch(ComplicationDiseaseDef cdef, ProgressComponent prog,
                                        SymptomPoolComponent pool, SourceComponent src, TierComponent tier,
                                        ServerPlayer player, PlayerDiseaseState state, DiseaseInstance instance) {
        ResourceLocation sourceId = cdef.triggeredById().get();
        boolean gateOpen = isBacterialGateActive(sourceId, state);
        if (gateOpen) {
            if (!src.hasSource()) src.sourceId = sourceId;
            double rate = cdef.accumulationRate().orElse(1.0 / 12000.0);
            prog.add(rate, cdef.latchThreshold());
        } else {
            prog.add(-cdef.decayRate(), cdef.latchThreshold());
            if (prog.progress <= 0.0) { clearComplication(player, cdef, instance); return; }
        }
        syncComplicationPool(player, state, pool, cdef, src, tier, prog);
        if (prog.progress >= cdef.latchThreshold() && src.hasSource()) {
            latch(player, state, cdef, pool, src, tier, prog);
            prog.inRecovery = true;
        }
    }

    private void tickViralPreLatch(ComplicationDiseaseDef cdef, ProgressComponent prog,
                                    SymptomPoolComponent pool, SourceComponent src, TierComponent tier,
                                    ServerPlayer player, PlayerDiseaseState state, long gameTime,
                                    DiseaseInstance instance) {
        boolean immune = state.isGroupImmune(cdef.exclusionGroup(), gameTime);
        ResourceLocation qualifying = immune ? null : qualifyingSource(cdef, state, player);
        if (qualifying != null) {
            if (!src.hasSource()) {
                src.sourceId   = qualifying;
                src.latchTicks = randomLatchTicks(player, cdef);
            }
            prog.add(accumRate(player, src), cdef.progressCap());
        } else if (src.hasSource() && prog.progress > 0.0 && prog.progress < cdef.latchThreshold()) {
            prog.add(-cdef.decayRate(), cdef.progressCap());
            if (prog.progress <= 0.0) { clearComplication(player, cdef, instance); return; }
        }
        syncComplicationPool(player, state, pool, cdef, src, tier, prog);
        if (prog.progress >= cdef.latchThreshold() && src.hasSource()) {
            latch(player, state, cdef, pool, src, tier, prog);
            prog.inRecovery = true;
        }
    }

    // =========================================================================
    // POST-LATCH
    // =========================================================================

    private void tickPostLatch(ComplicationDiseaseDef cdef, ProgressComponent prog, SymptomPoolComponent pool,
                                SourceComponent src, TierComponent tier,
                                ServerPlayer player, PlayerDiseaseState state, long gameTime,
                                DiseaseInstance instance, boolean recoverySuppressed, boolean worseningConditions) {
        // Defensive: latched without a roll (e.g. debug /sdaccumulate bump).
        if (!tier.rolled()) rollBiasedTier(cdef, tier, player, state, src.sourceId);

        ensureEffect(player, cdef, src, tier);

        // Viral-gate: emit source-disease particles (looks contagious for the source).
        if (cdef.triggeredBy().isEmpty() && src.sourceId != null
                && DiseaseRegistry.get(src.sourceId) instanceof ViralDiseaseDef sv) {
            DiseaseParticleEmitter.tick(player, sv.particle().get());
        }

        if (cdef.deterministicWorsening()) {
            prog.add(cdef.worseningRate(), cdef.progressCap());
            tickDeterministicWorsening(cdef, pool, src, tier, prog.progress, player);
        } else {
            if (worseningConditions) {
                prog.add(accumRate(player, src), cdef.progressCap());
            } else if (!recoverySuppressed) {
                double recovRate = effectiveRecoveryRate(cdef, src);
                prog.add(-recovRate, cdef.progressCap());
                if (prog.progress <= 0.0) { cure(player, state, cdef, instance, gameTime); return; }
            }
            tickStochasticWorsening(cdef, pool, src, tier, prog.progress, player);
        }

        ensureEffect(player, cdef, src, tier);

        // Multiple Organ Failure: once latched, deal 1 magic damage every 40 ticks (Wither I rate)
        // without applying the Wither effect. Death is certain without treatment.
        if (cdef.id().equals(DiseaseRegistry.MOF_STAPH) && gameTime % 40 == 0) {
            player.hurt(player.level().damageSources().magic(), 1.0f);
        }

        if (cdef.symptoms().symptomBits() > 0) {
            MobEffect diseaseEff = src.sourceId != null
                    ? DiseaseEffects.complicationVariant(cdef.id(), src.sourceId, tier.severity()).get()
                    : null;
            SymptomService.tickEpisodes(player, pool, cdef.symptoms(), gameTime, tier.severity(), diseaseEff);
            SymptomService.ensureStaticMarkers(player, pool, cdef.symptoms());
        }
    }

    // =========================================================================
    // LATCH
    // =========================================================================

    private void latch(ServerPlayer player, PlayerDiseaseState state, ComplicationDiseaseDef cdef,
                       SymptomPoolComponent pool, SourceComponent src, TierComponent tier, ProgressComponent prog) {
        ResourceLocation sourceId = src.sourceId;

        // Bronchitis → pneumonia upgrade: clear bronchitis before latching pneumonia.
        if (cdef.id().equals(DiseaseRegistry.PNEUMONIA) && state.inRecovery(DiseaseRegistry.BRONCHITIS)) {
            ResourceLocation bronchSource = state.complicationSource(DiseaseRegistry.BRONCHITIS);
            if (sourceId != null && sourceId.equals(bronchSource)) {
                DiseaseInstance bi = state.peek(DiseaseRegistry.BRONCHITIS);
                DiseaseDef bronchitisDef = DiseaseRegistry.get(DiseaseRegistry.BRONCHITIS);
                if (bi != null && bronchitisDef instanceof ComplicationDiseaseDef cbd) {
                    clearComplication(player, cbd, bi);
                }
            }
        }

        // Capture source state before clearing it.
        Severity sourceTier   = state.tierOf(sourceId);
        double sourceProgress = Math.max(0.0, state.progress(sourceId));

        // Absorb source progress as a head-start.
        prog.progress = Math.min(cdef.progressCap(), prog.progress + sourceProgress);

        // Clear (absorb) the source disease entirely.
        absorbSource(player, state, sourceId);

        // Biased-roll at first threshold (pre-latch); defensive roll only if latch happens without one.
        if (!tier.rolled()) rollBiasedTier(cdef, tier, player, sourceTier);

        tier.reductions                = 0;
        tier.worseningChecks           = 0;
        tier.worsenings                = 0;
        tier.previousWorseningProgress = prog.progress;
        src.symptomBit                 = -1; // vestigial

        Severity severity = tier.severity();
        MobEffect eff = DiseaseEffects.complicationVariant(cdef.id(), sourceId, severity).get();
        player.addEffect(new MobEffectInstance(eff, -1, 0, false, false, true));

        // Pre-latch syncPool already built the symptom pool; reset episode timer from latch.
        pool.nextEpisodeAt = 0L;

        player.sendSystemMessage(Component.translatable(cdef.caughtKey()));
    }

    // =========================================================================
    // WORSENING
    // =========================================================================

    private void tickStochasticWorsening(ComplicationDiseaseDef cdef, SymptomPoolComponent pool,
                                          SourceComponent src, TierComponent tier,
                                          double progress, ServerPlayer player) {
        if (!tier.rolled()) { tier.previousWorseningProgress = progress; return; }
        List<Double> thresholds = cdef.worseningThresholds();
        int maxSevOrd = maxSeverityOrdinal(cdef);
        for (int i = 0; i < thresholds.size(); i++) {
            int bit = 1 << i;
            double threshold = thresholds.get(i);
            if ((tier.worseningChecks & bit) != 0
                    || tier.previousWorseningProgress >= threshold
                    || progress < threshold) continue;
            tier.worseningChecks |= bit;
            if (tier.severity >= maxSevOrd) continue;
            float chance = (float) Math.min(1.0, STOCHASTIC_BASE_CHANCE + STOCHASTIC_MOMENTUM * tier.worsenings);
            if (player.getRandom().nextFloat() < chance) {
                Severity oldTier = Severity.byOrdinal(tier.severity);
                tier.severity++;
                tier.worsenings++;
                tryUpgradeAfterWorsen(player, pool, cdef, src, tier, oldTier);
                sendWorsensMessage(player, cdef);
            }
        }
        tier.previousWorseningProgress = progress;
    }

    private void tickDeterministicWorsening(ComplicationDiseaseDef cdef, SymptomPoolComponent pool,
                                             SourceComponent src, TierComponent tier,
                                             double progress, ServerPlayer player) {
        if (!tier.rolled()) { tier.previousWorseningProgress = progress; return; }
        List<Double> thresholds = cdef.worseningThresholds();
        int maxSevOrd = maxSeverityOrdinal(cdef);
        for (int i = 0; i < thresholds.size(); i++) {
            int bit = 1 << i;
            if ((tier.worseningChecks & bit) != 0 || progress < thresholds.get(i)) continue;
            tier.worseningChecks |= bit;
            if (tier.severity < maxSevOrd) {
                Severity oldTier = Severity.byOrdinal(tier.severity);
                tier.severity++;
                tryUpgradeAfterWorsen(player, pool, cdef, src, tier, oldTier);
                sendWorsensMessage(player, cdef);
            }
        }
        tier.previousWorseningProgress = progress;
    }

    private static void sendWorsensMessage(ServerPlayer player, ComplicationDiseaseDef cdef) {
        if (!cdef.worsensKey().isEmpty()) {
            player.sendSystemMessage(Component.translatable(cdef.worsensKey()));
        } else {
            player.sendSystemMessage(Component.literal("§cYour condition worsens."));
        }
    }

    // =========================================================================
    // EFFECT MANAGEMENT
    // =========================================================================

    private void ensureEffect(ServerPlayer player, ComplicationDiseaseDef cdef, SourceComponent src, TierComponent tier) {
        if (src.sourceId == null) return;
        Severity severity = tier.rolled() ? tier.severity() : cdef.lowestSeverity();
        DiseaseEffects.removeOtherComplicationVariants(player, cdef.id(), src.sourceId, severity);
        MobEffect eff = DiseaseEffects.complicationVariant(cdef.id(), src.sourceId, severity).get();
        if (!player.hasEffect(eff)) player.addEffect(new MobEffectInstance(eff, -1, 0, false, false, true));
    }

    // =========================================================================
    // CURE / RESET
    // =========================================================================

    private void cure(ServerPlayer player, PlayerDiseaseState state, ComplicationDiseaseDef cdef,
                      DiseaseInstance instance, long gameTime) {
        ProgressComponent    prog = instance.get(Components.PROGRESS);
        SymptomPoolComponent pool = instance.get(Components.SYMPTOMS);
        SourceComponent      src  = instance.get(Components.SOURCE);
        TierComponent        tier = instance.get(Components.TIER);

        DiseaseEffects.removeComplication(player, cdef.id());
        SymptomService.clearActive(player, pool, cdef.symptoms());
        pool.clearAll(); pool.nextEpisodeAt = 0L;
        prog.inRecovery = false; prog.progress = 0.0;
        resetTier(tier);
        state.grantGroupImmunity(cdef.exclusionGroup(), gameTime, DiseaseRegistry.VIRAL_IMMUNITY_TICKS);
        src.clear();
        player.sendSystemMessage(Component.translatable(cdef.curedKey()));
    }

    private void clearComplication(ServerPlayer player, ComplicationDiseaseDef cdef, DiseaseInstance instance) {
        DiseaseEffects.removeComplication(player, cdef.id());
        ProgressComponent    prog = instance.get(Components.PROGRESS);
        SymptomPoolComponent pool = instance.get(Components.SYMPTOMS);
        SourceComponent      src  = instance.get(Components.SOURCE);
        TierComponent        tier = instance.get(Components.TIER);
        SymptomService.clearActive(player, pool, cdef.symptoms());
        if (prog != null) { prog.progress = 0.0; prog.inRecovery = false; }
        if (pool != null) { pool.clearAll(); pool.nextEpisodeAt = 0L; }
        if (src  != null) src.clear();
        if (tier != null) resetTier(tier);
    }

    // =========================================================================
    // STATIC GATE / QUALIFICATION HELPERS (used by DiseaseEvents)
    // =========================================================================

    /** The viral complication that should begin developing right now (if any). */
    public static ComplicationDiseaseDef qualifyingComplication(PlayerDiseaseState state, ServerPlayer player) {
        ComplicationDiseaseDef upgrade = qualifyingUpgrade(null, state, player);
        if (upgrade != null) return upgrade;

        boolean immunodef = ImmuneManager.isImmunodeficient(player);

        Severity flu = activeTier(state, DiseaseRegistry.FLU);
        if (flu != null) {
            if (flu == Severity.DEBILITATING) return complicationDef(DiseaseRegistry.PNEUMONIA);
            if (flu == Severity.SEVERE) return pickComplication(player, 0.40f);
            if (flu == Severity.MODERATE) return immunodef
                    ? pickComplication(player, 0.60f)
                    : complicationDef(DiseaseRegistry.BRONCHITIS);
        }

        Severity cold = activeTier(state, DiseaseRegistry.COLD);
        if (immunodef && cold != null && cold.ordinal() >= Severity.SEVERE.ordinal())
            return pickComplication(player, 0.60f);

        Severity rsv = activeTier(state, DiseaseRegistry.RSV);
        if (rsv != null) {
            if (immunodef && rsv.ordinal() >= Severity.SEVERE.ordinal()) return pickComplication(player, 0.60f);
            if (rsv.ordinal() >= Severity.SEVERE.ordinal()
                    || (immunodef && rsv.ordinal() >= Severity.MODERATE.ordinal())) {
                return complicationDef(DiseaseRegistry.BRONCHITIS);
            }
        }
        return null;
    }

    /**
     * The bacterial complication seeded directly by a wound-seeded bacterial disease (e.g. sepsis
     * triggered by cellulitis). Excludes complications triggered by other complications (those are
     * handled by {@link #qualifyingMofComplication}).
     */
    public static ComplicationDiseaseDef qualifyingBacterialComplication(PlayerDiseaseState state) {
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!(def instanceof ComplicationDiseaseDef cdef)) continue;
            if (cdef.triggeredById().isEmpty()) continue;
            ResourceLocation sourceId = cdef.triggeredById().get();
            if (!(DiseaseRegistry.get(sourceId) instanceof BacterialDiseaseDef)) continue;
            if (isBacterialGateActive(sourceId, state)) return cdef;
        }
        return null;
    }

    /**
     * The late-stage complication triggered by another complication reaching max tier (e.g. MOF
     * triggered by Debilitating sepsis). Separate slot from the bacterial complication so both can
     * be active simultaneously.
     */
    public static ComplicationDiseaseDef qualifyingMofComplication(PlayerDiseaseState state) {
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!(def instanceof ComplicationDiseaseDef cdef)) continue;
            if (cdef.triggeredById().isEmpty()) continue;
            ResourceLocation sourceId = cdef.triggeredById().get();
            if (!(DiseaseRegistry.get(sourceId) instanceof ComplicationDiseaseDef)) continue;
            if (isBacterialGateActive(sourceId, state)) return cdef;
        }
        return null;
    }

    /** Narrow upgrade path: severe-or-worse bronchitis may develop into pneumonia. */
    public static ComplicationDiseaseDef qualifyingUpgrade(ResourceLocation activeComplicationId,
                                                            PlayerDiseaseState state, ServerPlayer player) {
        if (activeComplicationId != null && !activeComplicationId.equals(DiseaseRegistry.BRONCHITIS)) return null;
        if (state.inRecovery(DiseaseRegistry.PNEUMONIA)) return null;
        Severity bronchitis = activeTier(state, DiseaseRegistry.BRONCHITIS);
        if (bronchitis == null || bronchitis.ordinal() < Severity.SEVERE.ordinal()) return null;
        if (state.complicationSource(DiseaseRegistry.BRONCHITIS) == null) return null;
        return complicationDef(DiseaseRegistry.PNEUMONIA);
    }

    /** Source disease that currently qualifies the player for any viral complication. */
    public static ResourceLocation qualifyingSource(PlayerDiseaseState state, ServerPlayer player) {
        ComplicationDiseaseDef cdef = qualifyingComplication(state, player);
        return cdef == null ? null : qualifyingSource(cdef, state, player);
    }

    /** Source disease that qualifies the player for this specific viral complication. */
    public static ResourceLocation qualifyingSource(ComplicationDiseaseDef cdef, PlayerDiseaseState state, ServerPlayer player) {
        if (cdef.id().equals(DiseaseRegistry.BRONCHITIS)) return qualifyingBronchitisSource(state, player);
        return qualifyingPneumoniaSource(state, player);
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Whether the gate for a triggered complication is open: source disease latched, progress > 0,
     * and at max tier. Accepts BacterialDiseaseDef (cellulitis→sepsis) and ComplicationDiseaseDef
     * (sepsis→MOF) sources.
     */
    private static boolean isBacterialGateActive(ResourceLocation sourceId, PlayerDiseaseState state) {
        DiseaseDef sourceDef = DiseaseRegistry.get(sourceId);
        if (sourceDef == null) return false;
        if (!state.inRecovery(sourceId)) return false;
        if (state.progress(sourceId) <= 0.0) return false;
        Severity srcTier = state.tierOf(sourceId);
        if (srcTier == null) return false;
        List<Severity> srcTiers = sourceTiers(sourceDef);
        return srcTier.ordinal() >= srcTiers.get(srcTiers.size() - 1).ordinal();
    }

    private static List<Severity> sourceTiers(DiseaseDef sourceDef) {
        if (sourceDef instanceof BacterialDiseaseDef b)   return b.tiers();
        if (sourceDef instanceof ComplicationDiseaseDef c) return c.tiers();
        return List.of(Severity.MODERATE);
    }

    private static void tryUpgradeAfterWorsen(ServerPlayer player, SymptomPoolComponent pool,
                                               ComplicationDiseaseDef cdef, SourceComponent src,
                                               TierComponent tier, Severity oldTier) {
        MobEffect diseaseEff = src.sourceId != null
                ? DiseaseEffects.complicationVariant(cdef.id(), src.sourceId, tier.severity()).get()
                : null;
        SymptomService.tryUpgradeIncidental(player, pool, cdef.symptoms(), oldTier, tier.severity(), diseaseEff);
    }

    private void syncComplicationPool(ServerPlayer player, PlayerDiseaseState state, SymptomPoolComponent pool,
                                       ComplicationDiseaseDef cdef, SourceComponent src, TierComponent tier,
                                       ProgressComponent prog) {
        List<Double> thresholds = cdef.symptoms().thresholds();
        double firstThreshold = thresholds.isEmpty() ? cdef.latchThreshold() : thresholds.get(0);
        if (prog.progress >= firstThreshold && !tier.rolled() && src.hasSource()) {
            Severity sourceTier = state.tierOf(src.sourceId);
            if (sourceTier != null) rollBiasedTier(cdef, tier, player, sourceTier);
        }
        Severity sev = tier.rolled() ? tier.severity() : Severity.MODERATE;
        SymptomService.syncPool(player, pool, cdef.symptoms(), prog.progress, sev, null,
                sourceSnapshot(state, src));
    }

    private static Optional<SourceSymptomSnapshot> sourceSnapshot(PlayerDiseaseState state, SourceComponent src) {
        if (!src.hasSource()) return Optional.empty();
        ResourceLocation sourceId = src.sourceId;
        DiseaseInstance sourceInst = state.peek(sourceId);
        DiseaseDef sourceDef = DiseaseRegistry.get(sourceId);
        if (sourceInst == null || sourceDef == null) return Optional.empty();
        SymptomPoolComponent sourcePool = sourceInst.get(Components.SYMPTOMS);
        if (sourcePool == null) return Optional.empty();
        SymptomConfig sourceConfig = symptomsOf(sourceDef);
        if (sourceConfig == null) return Optional.empty();
        return Optional.of(new SourceSymptomSnapshot(sourceConfig, sourcePool.mask));
    }

    private static SymptomConfig symptomsOf(DiseaseDef def) {
        if (def instanceof ViralDiseaseDef v) return v.symptoms();
        if (def instanceof BacterialDiseaseDef b) return b.symptoms();
        if (def instanceof ComplicationDiseaseDef c) return c.symptoms();
        return null;
    }

    private void rollBiasedTier(ComplicationDiseaseDef cdef, TierComponent tier,
                                  ServerPlayer player, PlayerDiseaseState state, ResourceLocation sourceId) {
        Severity sourceTier = sourceId != null ? state.tierOf(sourceId) : null;
        rollBiasedTier(cdef, tier, player, sourceTier);
    }

    private void rollBiasedTier(ComplicationDiseaseDef cdef, TierComponent tier,
                                  ServerPlayer player, Severity sourceTier) {
        int sourceTierOrd = sourceTier != null ? sourceTier.ordinal() : Severity.MODERATE.ordinal();
        int baseRolls;
        if (sourceTierOrd >= Severity.DEBILITATING.ordinal()) {
            baseRolls = cdef.tiers().size();
        } else if (sourceTierOrd >= Severity.SEVERE.ordinal()) {
            baseRolls = 3;
        } else if (sourceTierOrd >= Severity.MODERATE.ordinal()) {
            baseRolls = 2;
        } else {
            baseRolls = 1;
        }
        // ImmuneManager.getSeverityRolls returns 1 for normal, 2+ for immunodeficient, etc.
        int rolls = Math.max(1, baseRolls + (ImmuneManager.getSeverityRolls(player) - 1));
        tier.severity   = Severity.rollWeightedBiased(cdef.tiers(), player.getRandom(), rolls).ordinal();
        tier.reductions = 0;
    }

    private double accumRate(ServerPlayer player, SourceComponent src) {
        long ticks = src.latchTicks > 0 ? src.latchTicks : 24000L;
        return (1.0 / ticks) * ImmuneManager.getComplicationMultiplier(player);
    }

    private double effectiveRecoveryRate(ComplicationDiseaseDef cdef, SourceComponent src) {
        if (cdef.passiveRecoveryRate().isPresent()) return cdef.passiveRecoveryRate().get();
        if (cdef.triggeredBy().isPresent()) return 0.0; // bacterial gate: no passive recovery
        DiseaseDef sdef = src.sourceId == null ? null : DiseaseRegistry.get(src.sourceId);
        return (sdef instanceof ViralDiseaseDef v) ? v.recoveryRate() : FALLBACK_RECOVERY_RATE;
    }

    private long randomLatchTicks(ServerPlayer player, ComplicationDiseaseDef cdef) {
        long span = Math.max(1L, cdef.maxLatchTicks() - cdef.minLatchTicks());
        return cdef.minLatchTicks() + (long) (player.getRandom().nextDouble() * span);
    }

    private static int maxSeverityOrdinal(ComplicationDiseaseDef def) {
        return Math.min(Severity.DEBILITATING.ordinal(), Severity.MODERATE.ordinal() + def.tierCount() / 2);
    }

    /** Clears source disease effects, symptoms, tier, and progress when absorbed at latch. */
    private static void absorbSource(ServerPlayer player, PlayerDiseaseState state, ResourceLocation sourceId) {
        DiseaseDef sourceDef = DiseaseRegistry.get(sourceId);
        DiseaseInstance sourceInst = state.peek(sourceId);
        if (sourceInst == null) return;
        SymptomPoolComponent srcPool = sourceInst.get(Components.SYMPTOMS);
        TierComponent        srcTier = sourceInst.get(Components.TIER);
        ProgressComponent    srcProg = sourceInst.get(Components.PROGRESS);
        if (sourceDef instanceof ViralDiseaseDef v) {
            v.removeEffects(player);
            if (srcPool != null) SymptomService.clearActive(player, srcPool, v.symptoms());
        } else if (sourceDef instanceof BacterialDiseaseDef b) {
            b.removeEffects(player);
            if (srcPool != null) SymptomService.clearActive(player, srcPool, b.symptoms());
        }
        if (srcPool != null) { srcPool.clearAll(); srcPool.nextEpisodeAt = 0L; }
        if (srcTier != null) {
            srcTier.severity = -1; srcTier.reductions = 0;
            srcTier.worseningChecks = 0; srcTier.worsenings = 0;
            srcTier.previousWorseningProgress = 0.0;
        }
        if (srcProg != null) { srcProg.progress = 0.0; srcProg.inRecovery = false; }
    }

    private static void resetTier(TierComponent tier) {
        tier.severity = -1; tier.reductions = 0;
        tier.worseningChecks = 0; tier.worsenings = 0;
        tier.previousWorseningProgress = 0.0;
    }

    private static Severity activeTier(PlayerDiseaseState state, ResourceLocation id) {
        return state.inRecovery(id) ? state.tierOf(id) : null;
    }

    private static ComplicationDiseaseDef pickComplication(ServerPlayer player, float bronchitisChance) {
        return complicationDef(player.getRandom().nextFloat() < bronchitisChance
                ? DiseaseRegistry.BRONCHITIS : DiseaseRegistry.PNEUMONIA);
    }

    private static ComplicationDiseaseDef complicationDef(ResourceLocation id) {
        return DiseaseRegistry.get(id) instanceof ComplicationDiseaseDef c ? c : null;
    }

    private static ResourceLocation qualifyingPneumoniaSource(PlayerDiseaseState state, ServerPlayer player) {
        Severity bronchitis = activeTier(state, DiseaseRegistry.BRONCHITIS);
        if (bronchitis != null && bronchitis.ordinal() >= Severity.SEVERE.ordinal()) {
            ResourceLocation source = state.complicationSource(DiseaseRegistry.BRONCHITIS);
            if (source != null) return source;
        }
        boolean immunodef = ImmuneManager.isImmunodeficient(player);
        Severity flu = activeTier(state, DiseaseRegistry.FLU);
        if (flu != null) {
            if (flu.ordinal() >= Severity.SEVERE.ordinal()) return DiseaseRegistry.FLU;
            if (immunodef && flu.ordinal() >= Severity.MODERATE.ordinal()) return DiseaseRegistry.FLU;
        }
        if (immunodef) {
            Severity cold = activeTier(state, DiseaseRegistry.COLD);
            if (cold != null && cold.ordinal() >= Severity.SEVERE.ordinal()) return DiseaseRegistry.COLD;
            Severity rsv = activeTier(state, DiseaseRegistry.RSV);
            if (rsv != null && rsv.ordinal() >= Severity.SEVERE.ordinal()) return DiseaseRegistry.RSV;
        }
        return null;
    }

    private static ResourceLocation qualifyingBronchitisSource(PlayerDiseaseState state, ServerPlayer player) {
        boolean immunodef = ImmuneManager.isImmunodeficient(player);
        Severity flu = activeTier(state, DiseaseRegistry.FLU);
        if (flu != null && (flu == Severity.SEVERE || flu == Severity.MODERATE)) return DiseaseRegistry.FLU;
        Severity cold = activeTier(state, DiseaseRegistry.COLD);
        if (immunodef && cold != null && cold.ordinal() >= Severity.SEVERE.ordinal()) return DiseaseRegistry.COLD;
        Severity rsv = activeTier(state, DiseaseRegistry.RSV);
        if (rsv == null) return null;
        if (rsv.ordinal() >= Severity.SEVERE.ordinal()) return DiseaseRegistry.RSV;
        if (immunodef && rsv.ordinal() >= Severity.MODERATE.ordinal()) return DiseaseRegistry.RSV;
        return null;
    }
}
