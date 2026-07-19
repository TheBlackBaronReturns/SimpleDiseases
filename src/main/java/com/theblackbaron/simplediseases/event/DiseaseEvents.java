package com.theblackbaron.simplediseases.event;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.compat.ColdSweatCompat;
import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import com.theblackbaron.simplediseases.network.DebugOverlayPacket;
import com.theblackbaron.simplediseases.network.NetworkHandler;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.category.ComplicationCategory;
import com.theblackbaron.simplediseases.status.category.DiseaseContext;
import com.theblackbaron.simplediseases.status.category.DiseaseCategories;
import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.component.SourceComponent;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.component.TierComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.AccumFatigueManager;
import com.theblackbaron.simplediseases.status.manager.ContagionManager;
import com.theblackbaron.simplediseases.status.manager.FluSeasonManager;
import com.theblackbaron.simplediseases.status.manager.ImmuneManager;
import com.theblackbaron.simplediseases.status.manager.InjuryManager;
import com.theblackbaron.simplediseases.status.manager.LingeringNorovirusManager;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import com.theblackbaron.simplediseases.status.service.PersistentEffectService;
import com.theblackbaron.simplediseases.status.manager.WaterborneManager;
import com.theblackbaron.simplediseases.status.manager.WetnessManager;
import com.theblackbaron.simplediseases.status.manager.WindchillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DiseaseEvents {
    private static final String[] DAMP_ROMAN = {"I", "II", "III"};

    private static final double NULLIFY_THRESHOLD       = 0.05;
    private static final int    RESERVOIR_PARTICLE_INTERVAL = 8;
    private static final int    VOMIT_PARTICLE_TICKS        = 40;
    private static final int[]  COUGH_BURST_OFFSETS         = { 0, 8, 17, 26 };

    private static final int    CAP_RECOVERY_BIT            = 1 << 30;

    private final Map<UUID, PlayerDiseaseState> states                = new HashMap<>();
    private final WetnessManager                wetnessManager        = new WetnessManager();
    private final FluSeasonManager              fluSeasonManager      = new FluSeasonManager();
    private final LingeringNorovirusManager     lingering             = new LingeringNorovirusManager();
    private final ContagionManager              contagionManager      = new ContagionManager(states, lingering);
    private final InjuryManager                 injuryManager         = new InjuryManager();
    private final Set<UUID>                     debugViralPlayers     = new HashSet<>();
    private final Set<UUID>                     debugBacterialPlayers = new HashSet<>();
    private final Set<ResourceLocation>         suppressedEpisodeSourcesCache = new HashSet<>();
    private final Map<UUID, Boolean>            windchillCachedVal    = new HashMap<>();
    private final Map<UUID, Long>               windchillCachedAt     = new HashMap<>();
    private final Map<UUID, Long>               vomitParticleUntil    = new HashMap<>();
    private final Map<UUID, Integer>            lastVomitingDuration  = new HashMap<>();
    private final Map<UUID, CoughBurstSchedule> coughBurstSchedules   = new HashMap<>();
    private final Map<UUID, Integer>            lastBloodyCoughDuration     = new HashMap<>();
    private final Map<UUID, Integer>            lastProductiveCoughDuration = new HashMap<>();

    public ContagionManager  getContagionManager()       { return contagionManager; }
    public FluSeasonManager  getFluSeasonManager()        { return fluSeasonManager; }
    public Set<UUID>         getDebugViralPlayers()       { return debugViralPlayers; }
    public Set<UUID>         getDebugBacterialPlayers()   { return debugBacterialPlayers; }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = player.getPersistentData().getCompound(SimpleDiseases.MOD_ID);
        states.put(player.getUUID(), PlayerDiseaseState.loadFromNbt(root));
        NetworkHandler.sendDiseaseStateSync(player, root);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        saveToPlayer(player);
        UUID pid = player.getUUID();
        states.remove(pid);
        debugViralPlayers.remove(pid);
        debugBacterialPlayers.remove(pid);
        windchillCachedVal.remove(pid);
        windchillCachedAt.remove(pid);
        vomitParticleUntil.remove(pid);
        lastVomitingDuration.remove(pid);
        coughBurstSchedules.remove(pid);
        lastBloodyCoughDuration.remove(pid);
        lastProductiveCoughDuration.remove(pid);
        DiseaseParticleEmitter.clearVomitEmitState(pid);
        contagionManager.onPlayerLogout(pid);
    }

    @SubscribeEvent
    public void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.npc.Villager villager)) return;
        contagionManager.onEntityJoin(villager, event.loadedFromDisk());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        contagionManager.tickServerTick(server);
        ServerLevel overworld = server.overworld();
        lingering.tick(server, overworld.getGameTime());
        fluSeasonManager.tick(overworld);
        if (fluSeasonManager.consumeOutbreak()) {
            contagionManager.triggerFluOutbreak(overworld);
            overworld.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.simplediseases.flu_outbreak"), false);
        }
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        ServerPlayer original = (ServerPlayer) event.getOriginal();
        PlayerDiseaseState oldState = states.getOrDefault(
            original.getUUID(),
            PlayerDiseaseState.loadFromNbt(original.getPersistentData().getCompound(SimpleDiseases.MOD_ID))
        );
        PlayerDiseaseState newState = oldState.copy();
        if (event.isWasDeath()) newState.resetOnDeath();
        states.put(newPlayer.getUUID(), newState);
        saveToPlayer(newPlayer, newState);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        PlayerDiseaseState state    = contagionManager.getOrCreate(player);
        long               gameTime = player.level().getGameTime();

        wetnessManager.tick(player, state);
        injuryManager.tick(player, state);
        enforceMaxHealthCap(player);
        tickVomitParticles(player, gameTime);
        tickCoughParticles(player, gameTime);
        boolean isDamp = player.hasEffect(DiseaseEffects.DAMP.get());

        // Composite exclusion groups for the two organs handled directly in this method (respiratory:
        // damp/wind + contact incubation; GI: reservoir/puddle norovirus). Cheap map lookups, computed
        // once per tick rather than per-check.
        String respGroup = DiseaseRegistry.get(DiseaseRegistry.FLU).exclusionGroup();
        String noroGroup  = DiseaseRegistry.get(DiseaseRegistry.NOROVIRUS).exclusionGroup();

        boolean reservoirActive = false;
        boolean inReservoir = WaterborneManager.isInInfectedWater(player, state, gameTime);
        boolean noroLatched  = state.inRecovery(DiseaseRegistry.NOROVIRUS);
        boolean inPuddleZone = lingering.isInZone(player);
        boolean inLingering  = inPuddleZone && !noroLatched;
        ResourceLocation activeResp = state.activeInGroup(respGroup);

        // Norovirus (GI) is fully decoupled from whatever's active respiratory-side — the two organs
        // progress independently. Norovirus is the only gi_viral disease, so there's no same-organ rival
        // to switch away from; the only gate is its own immunity/complication state plus the slot cap.
        boolean noroInGroup = state.progress(DiseaseRegistry.NOROVIRUS) > 0.0 || noroLatched;
        boolean noroEligible = !state.isGroupImmune(noroGroup, gameTime)
                && !state.hasActiveComplication(noroGroup)
                && (noroInGroup || state.canStartNewSlot(noroGroup));
        if ((inReservoir || inLingering) && noroEligible) {
            double base = inReservoir ? WaterborneManager.exposureRate(player) : 0.0;
            if (inLingering) base = Math.max(base, WaterborneManager.submergedRate());
            state.addProgress(DiseaseRegistry.NOROVIRUS, base * ImmuneManager.getWaterborneMultiplier(player));
            reservoirActive = true;
        }

        boolean inInfectedWater = inReservoir || inPuddleZone;
        boolean enteredInfected = inInfectedWater && !state.wasInInfectedWater();
        if (enteredInfected && state.getPendingIncubation() <= 0.0 && !noroLatched && noroEligible) {
            ViralDiseaseDef noroDef = (ViralDiseaseDef) DiseaseRegistry.get(DiseaseRegistry.NOROVIRUS);
            state.setPendingIncubation(noroDef.rollIncubation(player.getRandom(), ImmuneManager.isImmunodeficient(player)),
                    DiseaseRegistry.NOROVIRUS);
        }
        state.setWasInInfectedWater(inInfectedWater);

        double pendingIncubation = state.getPendingIncubation();
        if (pendingIncubation > 0.0) {
            ResourceLocation incubationId = state.getPendingIncubationId();
            // Scoped to incubationId's own organ+pathogen group — it may be norovirus (GI, reservoir
            // entry) or cold/flu/rsv (respiratory, contact) — never compare across organs.
            String incGroup = incubationId != null ? DiseaseRegistry.get(incubationId).exclusionGroup() : null;
            ResourceLocation activeSameGroup = incGroup != null ? state.activeInGroup(incGroup) : null;
            boolean stickyOther = activeSameGroup != null && !activeSameGroup.equals(incubationId)
                    && (state.inRecovery(activeSameGroup) || state.progress(activeSameGroup) >= NULLIFY_THRESHOLD);
            if (incubationId == null || state.isGroupImmune(incGroup, gameTime)
                    || state.hasActiveComplication(incGroup) || stickyOther) {
                state.clearPendingIncubation();
            } else {
                if (activeSameGroup != null && !activeSameGroup.equals(incubationId)) state.clearProgress(activeSameGroup);
                double step = Math.min(WaterborneManager.baseExposureRate(), pendingIncubation);
                state.addProgress(incubationId, step);
                if (incGroup.equals(respGroup)) activeResp = incubationId;
                if (pendingIncubation - step <= 0.0) state.clearPendingIncubation();
                else state.setPendingIncubation(pendingIncubation - step, incubationId);
            }
        }

        if (inReservoir && gameTime % RESERVOIR_PARTICLE_INTERVAL == 0) {
            emitReservoirParticles(player);
        }

        double windRate = 0.0;
        boolean[] viralEnvFlag = { false };
        if (!reservoirActive) {
            if (isDamp && !player.isUnderWater()) {
                if (ColdSweatCompat.isColdEnoughForDamp(player)) {
                    double worldTemp = ColdSweatCompat.getWorldTemp(player);
                    double rate = ColdSweatCompat.getColdRate(worldTemp) * ImmuneManager.getDampMultiplier(player);
                    activeResp = accumulate(player, state, rate, activeResp, viralEnvFlag, respGroup);
                }
            } else if (!isDamp && cachedIsInWindchill(player, gameTime)) {
                if (ColdSweatCompat.isColdEnoughForWindchill(player)) {
                    windRate = WindchillManager.BASE_RATE
                            * WindchillManager.getMitigationFactor(player)
                            * ImmuneManager.getWindchillMultiplier(player);
                    activeResp = accumulate(player, state, windRate, activeResp, viralEnvFlag, respGroup);
                }
            }
        }
        boolean windActive = windRate > 0.0;
        boolean viralEnvAccumulatedThisTick = viralEnvFlag[0];
        tickChillyWindIndicator(player, state, windActive);

        boolean anyActive = activeResp != null || noroInGroup || reservoirActive
                || state.hasAnyActiveComplication()
                || state.progress(DiseaseRegistry.CELLULITIS_STAPH) > 0.0
                || state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH);

        double viralRecoveryMult = ColdSweatCompat.getRecoveryMultiplier(
                player, DiseaseRegistry.GROUP_VIRAL, viralEnvAccumulatedThisTick);
        double bacterialRecoveryMult = ColdSweatCompat.getRecoveryMultiplier(
                player, DiseaseRegistry.GROUP_BACTERIAL, false);

        if (anyActive) {
            String complicationWorseningGroup = (isDamp || windActive) ? DiseaseRegistry.GROUP_VIRAL : null;

            buildSuppressedEpisodeSources(state, suppressedEpisodeSourcesCache);

            DiseaseContext ctx = new DiseaseContext(player, viralEnvAccumulatedThisTick,
                    viralRecoveryMult, bacterialRecoveryMult, complicationWorseningGroup,
                    gameTime, lingering, state, suppressedEpisodeSourcesCache);
            tickExistingDiseases(state, ctx, false);
            tickExistingDiseases(state, ctx, true);
            tickPotentialComplications(player, state, ctx);

            // Respiratory and GI can both be latched at once now — emit particles for each independently.
            if (activeResp != null && state.inRecovery(activeResp) && DiseaseRegistry.get(activeResp) instanceof ViralDiseaseDef v) {
                DiseaseParticleEmitter.tick(player, v.particle().get());
            }
            if (state.inRecovery(DiseaseRegistry.NOROVIRUS)
                    && DiseaseRegistry.get(DiseaseRegistry.NOROVIRUS) instanceof ViralDiseaseDef nv) {
                DiseaseParticleEmitter.tick(player, nv.particle().get());
            }
        }

        AccumFatigueManager.tick(player, state, viralEnvAccumulatedThisTick);

        contagionManager.tick(player, state);
        PersistentEffectService.syncForPlayer(player, state);

        if (gameTime % 200 == Math.floorMod(player.getId(), 200)) saveToPlayer(player);

        UUID pid = player.getUUID();
        if (gameTime % 20 == 0) {
            boolean viralDbg = debugViralPlayers.contains(pid);
            boolean bacterialDbg = debugBacterialPlayers.contains(pid);
            if (viralDbg || bacterialDbg) {
                int mask = 0;
                List<String> viralLines = List.of();
                List<String> bacterialLines = List.of();
                if (viralDbg) {
                    mask |= DebugOverlayPacket.UPDATE_VIRAL;
                    viralLines = buildViralDebugLines(player, state, isDamp, viralEnvAccumulatedThisTick,
                            viralRecoveryMult);
                }
                if (bacterialDbg) {
                    mask |= DebugOverlayPacket.UPDATE_BACTERIAL;
                    bacterialLines = buildBacterialDebugLines(player, state, bacterialRecoveryMult);
                }
                NetworkHandler.sendDebugOverlay(player, mask, viralLines, bacterialLines);
            }
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        injuryManager.onPlayerDamaged(player, contagionManager.getOrCreate(player), event.getSource(), event.getAmount());
    }

    private void tickExistingDiseases(PlayerDiseaseState state, DiseaseContext ctx, boolean complications) {
        for (DiseaseInstance inst : state.instances()) {
            DiseaseDef def = DiseaseRegistry.get(inst.diseaseId());
            if (def == null || isComplication(def) != complications || !hasTickableState(inst)) continue;
            def.category().tick(def, inst, ctx);
        }
    }

    private void tickPotentialComplications(ServerPlayer player, PlayerDiseaseState state, DiseaseContext ctx) {
        boolean hasViralComp     = false;
        boolean hasBacterialComp = false;
        boolean hasMofComp       = false;

        // Scan existing complications: track which slots are occupied and check for upgrades. Bucketed
        // by gate mode (viral vs bacterial trigger), not by exclusion group — the group string no longer
        // equals the bare pathogen constants under the composite model.
        for (DiseaseDef existingDef : DiseaseRegistry.complications()) {
            DiseaseInstance existing = state.peek(existingDef.id());
            if (existing == null || !hasTickableState(existing)) continue;
            if (!(existingDef instanceof ComplicationDiseaseDef cdef)) continue;
            if (cdef.triggeredBy().isEmpty()) {
                hasViralComp = true;
                DiseaseDef upgrade = ComplicationCategory.qualifyingUpgrade(existingDef.id(), state, player);
                if (upgrade != null) {
                    DiseaseInstance inst = state.getOrCreate(upgrade.id());
                    upgrade.category().tick(upgrade, inst, ctx);
                    return; // upgrade handles this tick; don't also start fresh complications
                }
            } else {
                if (existingDef.id().equals(DiseaseRegistry.MOF_STAPH)) {
                    hasMofComp = true;
                } else {
                    hasBacterialComp = true;
                }
            }
        }

        // Start a new viral complication if no viral slot is occupied.
        if (!hasViralComp) {
            DiseaseDef viralComp = ComplicationCategory.qualifyingComplication(state, player);
            if (viralComp != null) {
                DiseaseInstance inst = state.getOrCreate(viralComp.id());
                viralComp.category().tick(viralComp, inst, ctx);
            }
        }

        // Start a new bacterial complication (sepsis) if the sepsis slot is unoccupied.
        if (!hasBacterialComp) {
            DiseaseDef bacterialComp = ComplicationCategory.qualifyingBacterialComplication(state);
            if (bacterialComp != null) {
                DiseaseInstance inst = state.getOrCreate(bacterialComp.id());
                bacterialComp.category().tick(bacterialComp, inst, ctx);
            }
        }

        // Start MOF if the MOF slot is unoccupied and sepsis has reached Debilitating.
        if (!hasMofComp) {
            DiseaseDef mofComp = ComplicationCategory.qualifyingMofComplication(state);
            if (mofComp != null) {
                DiseaseInstance inst = state.getOrCreate(mofComp.id());
                mofComp.category().tick(mofComp, inst, ctx);
            }
        }
    }

    private static void buildSuppressedEpisodeSources(PlayerDiseaseState state, Set<ResourceLocation> out) {
        out.clear();
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!(def instanceof ComplicationDiseaseDef cdef)) continue;
            DiseaseInstance cinst = state.peek(cdef.id());
            if (cinst == null) continue;
            ProgressComponent cp = cinst.get(Components.PROGRESS);
            if (cp == null || (cp.progress <= 0.0 && !cp.inRecovery)) continue;
            double firstThresh = cdef.symptoms().thresholds().isEmpty()
                    ? cdef.latchThreshold() : cdef.symptoms().thresholds().get(0);
            if (cp.progress >= firstThresh || cp.inRecovery) {
                SourceComponent csrc = cinst.get(Components.SOURCE);
                if (csrc != null && csrc.sourceId != null) out.add(csrc.sourceId);
            }
        }
    }

    private boolean cachedIsInWindchill(ServerPlayer player, long gameTime) {
        UUID pid = player.getUUID();
        Long at = windchillCachedAt.get(pid);
        if (at != null && gameTime - at <= 5L) return Boolean.TRUE.equals(windchillCachedVal.get(pid));
        boolean result = WindchillManager.isInWindchill(player);
        windchillCachedAt.put(pid, gameTime);
        windchillCachedVal.put(pid, result);
        return result;
    }

    private static boolean isComplication(DiseaseDef def) {
        return def.category() == DiseaseCategories.COMPLICATION;
    }

    private static boolean hasTickableState(DiseaseInstance inst) {
        ProgressComponent progress = inst.get(Components.PROGRESS);
        if (progress != null && (progress.progress > 0.0 || progress.inRecovery)) return true;
        SourceComponent source = inst.get(Components.SOURCE);
        if (source != null && source.hasSource()) return true;
        TierComponent tier = inst.get(Components.TIER);
        if (tier != null && tier.rolled()) return true;
        SymptomPoolComponent symptoms = inst.get(Components.SYMPTOMS);
        return symptoms != null && (symptoms.mask != 0 || symptoms.nextEpisodeAt != 0L);
    }

    private ResourceLocation accumulate(ServerPlayer player, PlayerDiseaseState state, double amount,
                                        ResourceLocation active, boolean[] viralEnvFlag, String respGroup) {
        if (amount <= 0.0) return active;
        long gameTime = player.level().getGameTime();
        if (active != null) {
            if (!active.equals(DiseaseRegistry.FLU) && !state.hasActiveComplication(respGroup)
                    && !state.isGroupImmune(respGroup, gameTime)
                    && canViralTakeHold(state, active, DiseaseRegistry.FLU)
                    && tryFluLockIn(player, state, amount, active, viralEnvFlag)) {
                return DiseaseRegistry.FLU;
            }
            state.addProgress(active, amount);
            viralEnvFlag[0] = true;
            return active;
        }
        if (state.isGroupImmune(respGroup, gameTime)) return null;
        if (state.hasActiveComplication(respGroup)) return null;
        if (!state.canStartNewSlot(respGroup)) return null;

        var level             = player.level();
        boolean winter        = SereneSeasonsCompat.isWinter(level);
        boolean fluWindowOpen = fluSeasonManager.isFluWindowOpen(level);
        boolean outbreak      = fluSeasonManager.isOutbreakActive(level);

        ViralDiseaseDef defaultDef = null;
        for (DiseaseDef def : DiseaseRegistry.environmental()) {
            ViralDiseaseDef v = (ViralDiseaseDef) def;
            if (v.acquisition().isDefault()) { defaultDef = v; continue; }
            double chance = v.acquisition().chance(winter, fluWindowOpen, outbreak);
            if (chance > 0.0 && player.getRandom().nextFloat() < chance) {
                state.addProgress(v.id(), amount);
                viralEnvFlag[0] = true;
                return v.id();
            }
        }
        if (defaultDef != null) {
            state.addProgress(defaultDef.id(), amount);
            viralEnvFlag[0] = true;
            return defaultDef.id();
        }
        return null;
    }

    /** True when {@code targetId} may replace {@code active} (sub-threshold, unlatched rival). */
    private static boolean canViralTakeHold(PlayerDiseaseState state, ResourceLocation active, ResourceLocation targetId) {
        if (active == null || active.equals(targetId)) return true;
        return !state.inRecovery(active) && state.progress(active) < NULLIFY_THRESHOLD;
    }

    /** Outbreak flu roll during damp/wind: clears a switchable rival and accumulates flu. */
    private boolean tryFluLockIn(ServerPlayer player, PlayerDiseaseState state, double amount,
                                 ResourceLocation active, boolean[] viralEnvFlag) {
        var level = player.level();
        if (!fluSeasonManager.isOutbreakActive(level)) return false;
        ViralDiseaseDef fluDef = (ViralDiseaseDef) DiseaseRegistry.get(DiseaseRegistry.FLU);
        if (fluDef == null) return false;
        boolean winter = SereneSeasonsCompat.isWinter(level);
        boolean fluWindowOpen = fluSeasonManager.isFluWindowOpen(level);
        double chance = fluDef.acquisition().chance(winter, fluWindowOpen, true);
        if (chance <= 0.0 || player.getRandom().nextFloat() >= chance) return false;
        state.clearProgress(active);
        state.addProgress(DiseaseRegistry.FLU, amount);
        viralEnvFlag[0] = true;
        return true;
    }

    private static void enforceMaxHealthCap(ServerPlayer player) {
        if (!DiseaseEffects.hasMaxHealthPenalty(player)) return;
        float max = player.getMaxHealth();
        float health = player.getHealth();
        if (health > max) {
            player.hurt(player.damageSources().magic(), health - max);
        }
    }

    private void emitReservoirParticles(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (player.isUnderWater()) {
            level.sendParticles(DiseaseParticles.NOROVIRUS.get(),
                    player.getX(), player.getY() + 0.5, player.getZ(), 4, 0.4, 0.4, 0.4, 0.01);
            return;
        }
        double surfaceY = waterSurfaceY(level, player);
        if (Double.isNaN(surfaceY)) return;
        level.sendParticles(DiseaseParticles.NOROVIRUS.get(),
                player.getX(), surfaceY, player.getZ(), 8, 0.6, 0.02, 0.6, 0.02);
    }

    private double waterSurfaceY(ServerLevel level, ServerPlayer player) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        if (!level.getBlockState(p).getFluidState().is(FluidTags.WATER)) {
            p.move(0, 1, 0);
            if (!level.getBlockState(p).getFluidState().is(FluidTags.WATER)) return Double.NaN;
        }
        int topWater = p.getY();
        for (int i = 0; i < 4; i++) {
            p.move(0, 1, 0);
            if (level.getBlockState(p).getFluidState().is(FluidTags.WATER)) topWater = p.getY();
            else break;
        }
        return topWater + 0.9;
    }

    private void tickChillyWindIndicator(ServerPlayer player, PlayerDiseaseState state, boolean impacted) {
        if (impacted) {
            int t = state.getWindchillExposureTicks() + 1;
            if (t >= WindchillManager.EFFECT_PERIOD_TICKS) {
                player.addEffect(new MobEffectInstance(
                        DiseaseEffects.CHILLY_WIND.get(), WindchillManager.EFFECT_DURATION_TICKS, 0, false, false, true));
                t = 0;
            }
            state.setWindchillExposureTicks(t);
        } else {
            state.setWindchillExposureTicks(0);
        }
    }

    // =========================================================================
    // VIRAL DEBUG OVERLAY (/sddebugviral)
    // =========================================================================

    private List<String> buildViralDebugLines(ServerPlayer player, PlayerDiseaseState state, boolean isDamp,
                                              boolean viralEnvAccum, double viralRecoveryMult) {
        long gameTime = player.level().getGameTime();
        double worldTemp = ColdSweatCompat.getWorldTemp(player);
        MobEffectInstance dampEffect = player.getEffect(DiseaseEffects.DAMP.get());
        String dampStr = dampEffect != null ? " D" + DAMP_ROMAN[dampEffect.getAmplifier()] : "";

        StringBuilder diseaseStr = new StringBuilder();
        // Respiratory-viral and GI-viral immunize independently under the composite exclusion model —
        // show both windows rather than one blanket "viral" timer.
        long respGroupImm = state.groupImmunityUntil(DiseaseRegistry.get(DiseaseRegistry.COLD).exclusionGroup()) - gameTime;
        if (respGroupImm > 0) diseaseStr.append(String.format(" §arIMM§r§7:§e%ds§r", respGroupImm / 20));
        long giGroupImm = state.groupImmunityUntil(DiseaseRegistry.get(DiseaseRegistry.NOROVIRUS).exclusionGroup()) - gameTime;
        if (giGroupImm > 0) diseaseStr.append(String.format(" §agIMM§r§7:§e%ds§r", giGroupImm / 20));
        for (DiseaseDef def : DiseaseRegistry.viral()) {
            ResourceLocation id = def.id();
            String name = id.getPath();
            if (state.inRecovery(id)) {
                diseaseStr.append(String.format(" §c%s§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), state.progress(id)));
            } else {
                double p = state.progress(id);
                if (p > 0) diseaseStr.append(String.format(" §6%s§r§7:§e%.3f§r", name, p));
            }
        }

        StringBuilder complicationStr = new StringBuilder();
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!DiseaseRegistry.GROUP_VIRAL.equals(def.pathogenType())) continue;
            ResourceLocation id = def.id();
            double prog = state.progress(id);
            ResourceLocation src = state.complicationSource(id);
            String srcTag = src != null ? "(" + src.getPath() + ")" : "";
            String name = id.getPath();
            if (state.inRecovery(id)) {
                complicationStr.append(String.format(" §c%s%s§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), srcTag, prog));
            } else if (prog > 0) {
                complicationStr.append(String.format(" §6%s%s§r§7:§e%.3f§r", name, srcTag, prog));
            }
        }

        String fluSeasonStr = fluSeasonManager.getFluSeason() != null
                ? " §d[" + fluSeasonManager.getFluSeason().name().charAt(0) + "]§r" : "";
        String windStr = (!isDamp && cachedIsInWindchill(player, gameTime))
                ? String.format(" §bWIND§r§7:§ex%.2f§r", WindchillManager.getMitigationFactor(player)
                    * ImmuneManager.getWindchillMultiplier(player)) : "";

        ResourceLocation activeId = null;
        for (DiseaseDef d : DiseaseRegistry.viral()) {
            if (state.inRecovery(d.id())) { activeId = d.id(); break; }
        }
        if (activeId == null) {
            for (DiseaseDef d : DiseaseRegistry.complications()) {
                if (DiseaseRegistry.GROUP_VIRAL.equals(d.pathogenType()) && state.inRecovery(d.id())) {
                    activeId = d.id(); break;
                }
            }
        }
        String epStr = buildEpStr(state, activeId, gameTime);

        boolean inReservoir = WaterborneManager.isInInfectedWater(player, state, gameTime);
        boolean inPuddle    = lingering.isInZone(player);
        StringBuilder noroSrcStr = new StringBuilder();
        if (inReservoir || inPuddle) {
            String tag = inReservoir && inPuddle ? "reservoir+puddle" : inReservoir ? "reservoir" : "puddle";
            noroSrcStr.append(" §2NORO-SRC§r§7:§a").append(tag).append("§r");
        }
        if (state.getPendingIncubation() > 0.0) {
            ResourceLocation incubationId = state.getPendingIncubationId();
            String incubationTag = incubationId != null ? incubationId.getPath() : "?";
            noroSrcStr.append(String.format(" §2INCUBATION(%s)§r§7:§a%.2f§r", incubationTag, state.getPendingIncubation()));
        }

        String accumStr = viralEnvAccum ? " §cACCUM§r" : "";
        long exposureSecs = state.getAccumFatigueStreakTicks() / 20L;
        double drainRate = latchedRecoveryRate(state, activeId) * viralRecoveryMult;
        String recoveryStr = String.format(" §frecov:§e%.2f§r%s §fexposure:§e%ds§r §fdrain:§e%.6f§r",
                viralRecoveryMult, accumStr, exposureSecs, drainRate);

        List<String> lines = new ArrayList<>(3);
        lines.add(String.format("§7[SDv]%s%s", diseaseStr, complicationStr));
        lines.add(String.format("§7%s%s%s%s", dampStr, fluSeasonStr, windStr, noroSrcStr));
        lines.add(String.format("§7%s%s §fwet:§e%.2f §fdry:§e%.4f §fW:§e%.1f",
                epStr, recoveryStr, state.getWetProgress(),
                ColdSweatCompat.getDryRate(player), worldTemp));
        return lines;
    }

    // =========================================================================
    // BACTERIAL DEBUG OVERLAY (/sddebugbacterial)
    // =========================================================================

    private List<String> buildBacterialDebugLines(ServerPlayer player, PlayerDiseaseState state,
                                                  double bacterialRecoveryMult) {
        long gameTime = player.level().getGameTime();

        StringBuilder bacterialStr = new StringBuilder();
        // Tissue-bacterial and systemic-bacterial immunize independently under the composite exclusion
        // model — show both windows rather than one blanket "bacterial" timer.
        long tissueGroupImm = state.groupImmunityUntil(DiseaseRegistry.get(DiseaseRegistry.CELLULITIS_STAPH).exclusionGroup()) - gameTime;
        if (tissueGroupImm > 0) bacterialStr.append(String.format(" §atIMM§r§7:§e%ds§r", tissueGroupImm / 20));
        long systemicGroupImm = state.groupImmunityUntil(DiseaseRegistry.get(DiseaseRegistry.SEPSIS_STAPH).exclusionGroup()) - gameTime;
        if (systemicGroupImm > 0) bacterialStr.append(String.format(" §asIMM§r§7:§e%ds§r", systemicGroupImm / 20));

        for (DiseaseDef def : DiseaseRegistry.bacterial()) {
            ResourceLocation id = def.id();
            double prog = state.progress(id);
            String name = id.getPath();
            if (state.inRecovery(id)) {
                Severity sev = state.tierOf(id);
                String sevStr = sev != null ? sev.name().substring(0, Math.min(4, sev.name().length())) : "?";
                bacterialStr.append(String.format(" §c%s§r§7[§c%s§7]§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), sevStr, prog));
            } else if (prog > 0) {
                bacterialStr.append(String.format(" §6%s§r§7:§e%.3f§r", name, prog));
            }
        }

        StringBuilder sepsisStr = new StringBuilder();
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!DiseaseRegistry.GROUP_BACTERIAL.equals(def.pathogenType())) continue;
            ResourceLocation id = def.id();
            double prog = state.progress(id);
            ResourceLocation src = state.complicationSource(id);
            String srcTag = src != null ? "(" + src.getPath() + ")" : "";
            String name = id.getPath();
            if (state.inRecovery(id)) {
                Severity sev = state.tierOf(id);
                String sevStr = sev != null ? sev.name().substring(0, Math.min(4, sev.name().length())) : "?";
                sepsisStr.append(String.format(" §c%s%s§r§7[§c%s§7]§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), srcTag, sevStr, prog));
            } else if (prog > 0) {
                sepsisStr.append(String.format(" §6%s%s§r§7:§e%.3f§r", name, srcTag, prog));
            }
        }

        int lacSev = state.injury().fleshWoundSeverity();
        String lacStr = lacSev >= 0 ? String.format(" §eFW:%d§r", lacSev) : "";

        ResourceLocation activeId = null;
        for (DiseaseDef d : DiseaseRegistry.bacterial()) {
            if (state.inRecovery(d.id())) { activeId = d.id(); break; }
        }
        if (activeId == null) {
            for (DiseaseDef d : DiseaseRegistry.complications()) {
                if (DiseaseRegistry.GROUP_BACTERIAL.equals(d.pathogenType()) && state.inRecovery(d.id())) {
                    activeId = d.id(); break;
                }
            }
        }
        String epStr = buildEpStr(state, activeId, gameTime);

        boolean cellulitisCap = isCellulitisCapRecovery(state);
        boolean onlyNonRecovering = state.inRecovery(DiseaseRegistry.SEPSIS_STAPH)
                || state.inRecovery(DiseaseRegistry.MOF_STAPH);
        boolean cellulitisRelevant = state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH)
                || state.progress(DiseaseRegistry.CELLULITIS_STAPH) > 0.0;
        double recovDisplay = cellulitisRelevant ? bacterialRecoveryMult
                : (onlyNonRecovering ? 0.0 : bacterialRecoveryMult);
        double drainRate = 0.0;
        if (cellulitisCap) {
            DiseaseDef cellDef = DiseaseRegistry.get(DiseaseRegistry.CELLULITIS_STAPH);
            if (cellDef instanceof BacterialDiseaseDef bdef) {
                drainRate = bdef.recoveryRate() * bacterialRecoveryMult;
            }
        }
        String recoveryStr = String.format(" §frecov:§e%.2f§r §fdrain:§e%.6f§r", recovDisplay, drainRate);

        List<String> lines = new ArrayList<>(2);
        lines.add(String.format("§7[SDb]%s%s%s", bacterialStr, sepsisStr, lacStr));
        lines.add(String.format("§7%s%s", epStr, recoveryStr));
        return lines;
    }

    private static boolean isCellulitisCapRecovery(PlayerDiseaseState state) {
        DiseaseInstance inst = state.peek(DiseaseRegistry.CELLULITIS_STAPH);
        if (inst == null) return false;
        ProgressComponent prog = inst.get(Components.PROGRESS);
        TierComponent tier = inst.get(Components.TIER);
        if (prog == null || tier == null || !prog.inRecovery) return false;
        if ((tier.worseningChecks & CAP_RECOVERY_BIT) != 0) return true;
        DiseaseDef def = DiseaseRegistry.get(DiseaseRegistry.CELLULITIS_STAPH);
        return def instanceof BacterialDiseaseDef bdef && prog.progress >= bdef.progressCap();
    }

    private static double latchedRecoveryRate(PlayerDiseaseState state, ResourceLocation activeId) {
        if (activeId == null || !state.inRecovery(activeId)) return 0.0;
        DiseaseDef def = DiseaseRegistry.get(activeId);
        if (def instanceof ViralDiseaseDef v) return v.recoveryRate();
        if (def instanceof ComplicationDiseaseDef cdef) {
            if (cdef.passiveRecoveryRate().isPresent()) return cdef.passiveRecoveryRate().get();
            if (cdef.triggeredBy().isPresent()) return 0.0;
            DiseaseInstance inst = state.peek(activeId);
            SourceComponent src = inst != null ? inst.get(Components.SOURCE) : null;
            ResourceLocation srcId = src != null ? src.sourceId : null;
            DiseaseDef sdef = srcId != null ? DiseaseRegistry.get(srcId) : null;
            return (sdef instanceof ViralDiseaseDef v) ? v.recoveryRate() : 0.00003;
        }
        return 0.0;
    }

    private String buildEpStr(PlayerDiseaseState state, ResourceLocation activeId, long gameTime) {
        if (activeId == null) return "";
        long next = state.nextEpisodeAt(activeId);
        long secs = next == 0L ? 0L : (next - gameTime) / 20L;
        Severity sev = state.tierOf(activeId);
        String sevStr = sev != null ? sev.name().substring(0, Math.min(4, sev.name().length())) : "?";
        return String.format(" §d%s:%s§r §aep:§e%ds§7/%dsym§r",
                activeId.getPath(), sevStr, Math.max(0L, secs), state.symptomCount(activeId));
    }

    private void tickVomitParticles(ServerPlayer player, long gameTime) {
        UUID uuid = player.getUUID();
        MobEffectInstance vomit = player.getEffect(DiseaseEffects.VOMITING.get());
        if (vomit != null) {
            int dur = vomit.getDuration();
            int prev = lastVomitingDuration.getOrDefault(uuid, 0);
            if (dur > prev + 10) {
                vomitParticleUntil.put(uuid, gameTime + VOMIT_PARTICLE_TICKS);
            }
            lastVomitingDuration.put(uuid, dur);
            if (gameTime < vomitParticleUntil.getOrDefault(uuid, 0L)) {
                DiseaseParticleEmitter.emitVomiting(player, gameTime);
            }
        } else {
            lastVomitingDuration.remove(uuid);
        }
    }

    private void tickCoughParticles(ServerPlayer player, long gameTime) {
        UUID uuid = player.getUUID();

        MobEffectInstance bloody = player.getEffect(DiseaseEffects.BLOODY_COUGHING.get());
        if (bloody != null) {
            int dur = bloody.getDuration();
            int prev = lastBloodyCoughDuration.getOrDefault(uuid, 0);
            if (dur > prev + 10) {
                scheduleCoughBursts(uuid, gameTime, DiseaseParticles.BLOODY_COUGH.get());
            }
            lastBloodyCoughDuration.put(uuid, dur);
        } else {
            lastBloodyCoughDuration.remove(uuid);
        }

        MobEffectInstance productive = player.getEffect(DiseaseEffects.PRODUCTIVE_COUGHING.get());
        if (productive != null) {
            int dur = productive.getDuration();
            int prev = lastProductiveCoughDuration.getOrDefault(uuid, 0);
            if (dur > prev + 10) {
                scheduleCoughBursts(uuid, gameTime, DiseaseParticles.SPUTUM.get());
            }
            lastProductiveCoughDuration.put(uuid, dur);
        } else {
            lastProductiveCoughDuration.remove(uuid);
        }

        CoughBurstSchedule schedule = coughBurstSchedules.get(uuid);
        if (schedule == null) return;

        while (schedule.nextBurstIndex < COUGH_BURST_OFFSETS.length
                && gameTime >= schedule.startTick + COUGH_BURST_OFFSETS[schedule.nextBurstIndex]) {
            DiseaseParticleEmitter.emitCoughSplatter(player, schedule.particle);
            schedule.nextBurstIndex++;
        }
        if (schedule.nextBurstIndex >= COUGH_BURST_OFFSETS.length) {
            coughBurstSchedules.remove(uuid);
        }
    }

    private void scheduleCoughBursts(UUID uuid, long gameTime, ParticleOptions particle) {
        CoughBurstSchedule schedule = new CoughBurstSchedule();
        schedule.startTick = gameTime;
        schedule.nextBurstIndex = 0;
        schedule.particle = particle;
        coughBurstSchedules.put(uuid, schedule);
    }

    private static final class CoughBurstSchedule {
        long startTick;
        int nextBurstIndex;
        ParticleOptions particle;
    }

    private void saveToPlayer(ServerPlayer player) {
        PlayerDiseaseState state = states.get(player.getUUID());
        if (state != null) saveToPlayer(player, state);
    }

    private void saveToPlayer(ServerPlayer player, PlayerDiseaseState state) {
        CompoundTag root = new CompoundTag();
        state.saveToNbt(root);
        player.getPersistentData().put(SimpleDiseases.MOD_ID, root);
        NetworkHandler.sendDiseaseStateSync(player, root);
    }
}
