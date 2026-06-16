package com.theblackbaron.simplediseases.event;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.compat.ColdSweatCompat;
import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
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
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import com.theblackbaron.simplediseases.status.manager.ContagionManager;
import com.theblackbaron.simplediseases.status.manager.FluSeasonManager;
import com.theblackbaron.simplediseases.status.manager.ImmuneManager;
import com.theblackbaron.simplediseases.status.manager.InjuryManager;
import com.theblackbaron.simplediseases.status.manager.LingeringNorovirusManager;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import com.theblackbaron.simplediseases.status.manager.WaterborneManager;
import com.theblackbaron.simplediseases.status.manager.WetnessManager;
import com.theblackbaron.simplediseases.status.manager.WindchillManager;
import net.minecraft.core.BlockPos;
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
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHealEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class DiseaseEvents {
    private static final String[] DAMP_ROMAN = {"I", "II", "III"};

    private static final double NULLIFY_THRESHOLD       = 0.05;
    private static final int    RESERVOIR_PARTICLE_INTERVAL = 8;

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

    public ContagionManager  getContagionManager()       { return contagionManager; }
    public FluSeasonManager  getFluSeasonManager()        { return fluSeasonManager; }
    public Set<UUID>         getDebugViralPlayers()       { return debugViralPlayers; }
    public Set<UUID>         getDebugBacterialPlayers()   { return debugBacterialPlayers; }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        CompoundTag root = player.getPersistentData().getCompound(SimpleDiseases.MOD_ID);
        states.put(player.getUUID(), PlayerDiseaseState.loadFromNbt(root));
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
        boolean isDamp = player.hasEffect(DiseaseEffects.DAMP.get());

        boolean reservoirActive = false;
        boolean inReservoir = WaterborneManager.isInInfectedWater(player, state, gameTime);
        boolean noroLatched  = state.inRecovery(DiseaseRegistry.NOROVIRUS);
        boolean inPuddleZone = lingering.isInZone(player);
        boolean inLingering  = inPuddleZone && !noroLatched;
        ResourceLocation active = activeViral(state);
        boolean hasViralComplication = state.hasActiveComplication(DiseaseRegistry.GROUP_VIRAL);
        if ((inReservoir || inLingering) && !state.isGroupImmune(DiseaseRegistry.GROUP_VIRAL, gameTime)
                && !hasViralComplication) {
            if (active == null || active.equals(DiseaseRegistry.NOROVIRUS)
                    || (!state.inRecovery(active) && state.progress(active) < NULLIFY_THRESHOLD)) {
                if (active != null && !active.equals(DiseaseRegistry.NOROVIRUS)) state.clearProgress(active);
                double base = inReservoir ? WaterborneManager.exposureRate(player) : 0.0;
                if (inLingering) base = Math.max(base, WaterborneManager.submergedRate());
                state.addProgress(DiseaseRegistry.NOROVIRUS, base * ImmuneManager.getWaterborneMultiplier(player));
                active = DiseaseRegistry.NOROVIRUS;
                reservoirActive = true;
            }
        }

        boolean inInfectedWater = inReservoir || inPuddleZone;
        boolean enteredInfected = inInfectedWater && !state.wasInInfectedWater();
        if (enteredInfected && state.getPendingIncubation() <= 0.0 && !noroLatched && !hasViralComplication
                && !state.isGroupImmune(DiseaseRegistry.GROUP_VIRAL, gameTime)) {
            boolean noroCanTakeHold = active == null || active.equals(DiseaseRegistry.NOROVIRUS)
                    || (!state.inRecovery(active) && state.progress(active) < NULLIFY_THRESHOLD);
            if (noroCanTakeHold) {
                ViralDiseaseDef noroDef = (ViralDiseaseDef) DiseaseRegistry.get(DiseaseRegistry.NOROVIRUS);
                state.setPendingIncubation(noroDef.rollIncubation(player.getRandom(), ImmuneManager.isImmunodeficient(player)),
                        DiseaseRegistry.NOROVIRUS);
            }
        }
        state.setWasInInfectedWater(inInfectedWater);

        double pendingIncubation = state.getPendingIncubation();
        if (pendingIncubation > 0.0) {
            ResourceLocation incubationId = state.getPendingIncubationId();
            boolean stickyOther = active != null && !active.equals(incubationId)
                    && (state.inRecovery(active) || state.progress(active) >= NULLIFY_THRESHOLD);
            if (incubationId == null || state.isGroupImmune(DiseaseRegistry.GROUP_VIRAL, gameTime) || hasViralComplication || stickyOther) {
                state.clearPendingIncubation();
            } else {
                if (active != null && !active.equals(incubationId)) state.clearProgress(active);
                double step = Math.min(WaterborneManager.baseExposureRate(), pendingIncubation);
                state.addProgress(incubationId, step);
                active = incubationId;
                if (pendingIncubation - step <= 0.0) state.clearPendingIncubation();
                else state.setPendingIncubation(pendingIncubation - step, incubationId);
            }
        }

        if (inReservoir && gameTime % RESERVOIR_PARTICLE_INTERVAL == 0) {
            emitReservoirParticles(player);
        }

        double windRate = 0.0;
        if (!reservoirActive) {
            if (isDamp && !player.isUnderWater()) {
                if (ColdSweatCompat.isColdEnoughForDamp(player)) {
                    double worldTemp = ColdSweatCompat.getWorldTemp(player);
                    double rate = ColdSweatCompat.getColdRate(worldTemp) * ImmuneManager.getDampMultiplier(player);
                    active = accumulate(player, state, rate, active);
                }
            } else if (!isDamp && cachedIsInWindchill(player, gameTime)) {
                if (ColdSweatCompat.isColdEnoughForWindchill(player)) {
                    windRate = WindchillManager.BASE_RATE
                            * WindchillManager.getMitigationFactor(player)
                            * ImmuneManager.getWindchillMultiplier(player);
                    active = accumulate(player, state, windRate, active);
                }
            }
        }
        boolean windActive = windRate > 0.0;
        tickChillyWindIndicator(player, state, windActive);

        boolean anyActive = active != null
                || state.hasActiveComplication(DiseaseRegistry.GROUP_VIRAL)
                || state.hasActiveComplication(DiseaseRegistry.GROUP_BACTERIAL)
                || state.progress(DiseaseRegistry.CELLULITIS_STAPH) > 0.0
                || state.inRecovery(DiseaseRegistry.CELLULITIS_STAPH);

        if (anyActive) {
            String suppressedGroup = (isDamp || windActive || !ColdSweatCompat.isWarmEnoughToRecover(player))
                    ? DiseaseRegistry.GROUP_VIRAL : null;
            String complicationWorseningGroup = (isDamp || windActive) ? DiseaseRegistry.GROUP_VIRAL : null;

            // Build the set of source-disease IDs whose episodes should be suppressed this tick.
            // A complication suppresses its source once it passes its first symptom threshold (0.1).
            buildSuppressedEpisodeSources(state, suppressedEpisodeSourcesCache);

            DiseaseContext ctx = new DiseaseContext(player, suppressedGroup, complicationWorseningGroup,
                    gameTime, lingering, state, suppressedEpisodeSourcesCache);
            tickExistingDiseases(state, ctx, false);
            tickExistingDiseases(state, ctx, true);
            tickPotentialComplications(player, state, ctx);

            if (active != null && state.inRecovery(active) && DiseaseRegistry.get(active) instanceof ViralDiseaseDef v) {
                DiseaseParticleEmitter.tick(player, v.particle().get());
            }
        }

        ColdSweatCompat.syncSepticShockModifier(player);

        contagionManager.tick(player, state);

        if (gameTime % 200 == Math.floorMod(player.getId(), 200)) saveToPlayer(player);

        UUID pid = player.getUUID();
        if (gameTime % 20 == 0) {
            if (debugViralPlayers.contains(pid))    showViralDebug(player, state, isDamp);
            if (debugBacterialPlayers.contains(pid)) showBacterialDebug(player, state);
        }
    }

    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;
        injuryManager.onPlayerDamaged(player, contagionManager.getOrCreate(player), event.getSource(), event.getAmount());
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.hasEffect(DiseaseEffects.BLOOD_LOSS.get())) {
            event.setCanceled(true);
        }
    }

    private ResourceLocation activeViral(PlayerDiseaseState state) {
        for (DiseaseDef def : DiseaseRegistry.viral()) {
            if (state.progress(def.id()) > 0 || state.inRecovery(def.id())) return def.id();
        }
        return null;
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

        // Scan existing complications: track which slots are occupied and check for upgrades.
        for (DiseaseDef existingDef : DiseaseRegistry.complications()) {
            DiseaseInstance existing = state.peek(existingDef.id());
            if (existing == null || !hasTickableState(existing)) continue;
            String group = existingDef.exclusionGroup();
            if (DiseaseRegistry.GROUP_VIRAL.equals(group)) {
                hasViralComp = true;
                DiseaseDef upgrade = ComplicationCategory.qualifyingUpgrade(existingDef.id(), state, player);
                if (upgrade != null) {
                    DiseaseInstance inst = state.getOrCreate(upgrade.id());
                    upgrade.category().tick(upgrade, inst, ctx);
                    return; // upgrade handles this tick; don't also start fresh complications
                }
            } else if (DiseaseRegistry.GROUP_BACTERIAL.equals(group)) {
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

    private ResourceLocation accumulate(ServerPlayer player, PlayerDiseaseState state, double amount, ResourceLocation active) {
        if (amount <= 0.0) return active;
        long gameTime = player.level().getGameTime();
        if (active != null) { state.addProgress(active, amount); return active; }
        if (state.isGroupImmune(DiseaseRegistry.GROUP_VIRAL, gameTime)) return null;
        if (state.hasActiveComplication(DiseaseRegistry.GROUP_VIRAL)) return null;

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
                return v.id();
            }
        }
        if (defaultDef != null) {
            state.addProgress(defaultDef.id(), amount);
            return defaultDef.id();
        }
        return null;
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

    private void showViralDebug(ServerPlayer player, PlayerDiseaseState state, boolean isDamp) {
        long gameTime = player.level().getGameTime();
        double worldTemp = ColdSweatCompat.getWorldTemp(player);
        MobEffectInstance dampEffect = player.getEffect(DiseaseEffects.DAMP.get());
        String dampStr = dampEffect != null ? " D" + DAMP_ROMAN[dampEffect.getAmplifier()] : "";

        StringBuilder diseaseStr = new StringBuilder();
        long groupImm = state.groupImmunityUntil(DiseaseRegistry.GROUP_VIRAL) - gameTime;
        if (groupImm > 0) diseaseStr.append(String.format(" §avIMM§r§7:§e%ds§r", groupImm / 20));
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

        // Viral complications (GROUP_VIRAL only)
        String complicationStr = "";
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!DiseaseRegistry.GROUP_VIRAL.equals(def.exclusionGroup())) continue;
            ResourceLocation id = def.id();
            double prog = state.progress(id);
            ResourceLocation src = state.complicationSource(id);
            String srcTag = src != null ? "(" + src.getPath() + ")" : "";
            String name = id.getPath();
            if (state.inRecovery(id)) {
                complicationStr += String.format(" §c%s%s§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), srcTag, prog);
            } else if (prog > 0) {
                complicationStr += String.format(" §6%s%s§r§7:§e%.3f§r", name, srcTag, prog);
            }
        }

        String fluSeasonStr = fluSeasonManager.getFluSeason() != null
                ? " §d[" + fluSeasonManager.getFluSeason().name().charAt(0) + "]§r" : "";
        String windStr = (!isDamp && cachedIsInWindchill(player, gameTime))
                ? String.format(" §bWIND§r§7:§ex%.2f§r", WindchillManager.getMitigationFactor(player)
                    * ImmuneManager.getWindchillMultiplier(player)) : "";

        // Episode info for the active viral disease or viral complication
        ResourceLocation activeId = null;
        for (DiseaseDef d : DiseaseRegistry.viral()) {
            if (state.inRecovery(d.id())) { activeId = d.id(); break; }
        }
        if (activeId == null) {
            for (DiseaseDef d : DiseaseRegistry.complications()) {
                if (DiseaseRegistry.GROUP_VIRAL.equals(d.exclusionGroup()) && state.inRecovery(d.id())) {
                    activeId = d.id(); break;
                }
            }
        }
        String epStr = buildEpStr(state, activeId, gameTime);

        // Norovirus source
        boolean inReservoir = WaterborneManager.isInInfectedWater(player, state, gameTime);
        boolean inPuddle    = lingering.isInZone(player);
        String noroSrcStr = "";
        if (inReservoir || inPuddle) {
            String tag = inReservoir && inPuddle ? "reservoir+puddle" : inReservoir ? "reservoir" : "puddle";
            noroSrcStr = " §2NORO-SRC§r§7:§a" + tag + "§r";
        }
        if (state.getPendingIncubation() > 0.0) {
            ResourceLocation incubationId = state.getPendingIncubationId();
            String incubationTag = incubationId != null ? incubationId.getPath() : "?";
            noroSrcStr += String.format(" §2INCUBATION(%s)§r§7:§a%.2f§r", incubationTag, state.getPendingIncubation());
        }

        String msg = String.format(
            "§7[SDv]%s%s%s%s%s%s%s §fwet:§e%.2f §fdry:§e%.4f §fW:§e%.1f",
            diseaseStr, complicationStr, dampStr, fluSeasonStr, windStr, epStr, noroSrcStr,
            state.getWetProgress(),
            ColdSweatCompat.getDryRate(player), worldTemp
        );
        player.displayClientMessage(Component.literal(msg), true);
    }

    // =========================================================================
    // BACTERIAL DEBUG OVERLAY (/sddebugbacterial)
    // =========================================================================

    private void showBacterialDebug(ServerPlayer player, PlayerDiseaseState state) {
        long gameTime = player.level().getGameTime();

        // Bacterial group immunity
        StringBuilder bacterialStr = new StringBuilder();
        long bGroupImm = state.groupImmunityUntil(DiseaseRegistry.GROUP_BACTERIAL) - gameTime;
        if (bGroupImm > 0) bacterialStr.append(String.format(" §abIMM§r§7:§e%ds§r", bGroupImm / 20));

        // Wound-seeded bacterial diseases (cellulitis)
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

        // Bacterial complications (GROUP_BACTERIAL in complicationList — sepsis)
        String sepsisStr = "";
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (!DiseaseRegistry.GROUP_BACTERIAL.equals(def.exclusionGroup())) continue;
            ResourceLocation id = def.id();
            double prog = state.progress(id);
            ResourceLocation src = state.complicationSource(id);
            String srcTag = src != null ? "(" + src.getPath() + ")" : "";
            String name = id.getPath();
            if (state.inRecovery(id)) {
                Severity sev = state.tierOf(id);
                String sevStr = sev != null ? sev.name().substring(0, Math.min(4, sev.name().length())) : "?";
                sepsisStr += String.format(" §c%s%s§r§7[§c%s§7]§r§7:§e%.3f§r", name.toUpperCase(Locale.ROOT), srcTag, sevStr, prog);
            } else if (prog > 0) {
                sepsisStr += String.format(" §6%s%s§r§7:§e%.3f§r", name, srcTag, prog);
            }
        }

        // Flesh wound
        int lacSev = state.injury().fleshWoundSeverity();
        String lacStr = lacSev >= 0 ? String.format(" §eFW:%d§r", lacSev) : "";

        // Episode info for the active bacterial/bacterial-complication disease
        ResourceLocation activeId = null;
        for (DiseaseDef d : DiseaseRegistry.bacterial()) {
            if (state.inRecovery(d.id())) { activeId = d.id(); break; }
        }
        if (activeId == null) {
            for (DiseaseDef d : DiseaseRegistry.complications()) {
                if (DiseaseRegistry.GROUP_BACTERIAL.equals(d.exclusionGroup()) && state.inRecovery(d.id())) {
                    activeId = d.id(); break;
                }
            }
        }
        String epStr = buildEpStr(state, activeId, gameTime);

        String msg = String.format("§7[SDb]%s%s%s%s", bacterialStr, sepsisStr, lacStr, epStr);
        player.displayClientMessage(Component.literal(msg), true);
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

    private void saveToPlayer(ServerPlayer player) {
        PlayerDiseaseState state = states.get(player.getUUID());
        if (state != null) saveToPlayer(player, state);
    }

    private void saveToPlayer(ServerPlayer player, PlayerDiseaseState state) {
        CompoundTag root = new CompoundTag();
        state.saveToNbt(root);
        player.getPersistentData().put(SimpleDiseases.MOD_ID, root);
    }
}
