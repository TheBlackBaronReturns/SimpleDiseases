package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.compat.SereneSeasonsCompat;
import com.theblackbaron.simplediseases.particle.DiseaseParticleEmitter;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.Severity;
import com.theblackbaron.simplediseases.status.def.ViralContagion;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * Disease transmission for all contagious (viral) diseases. One {@link Channel} per disease carries
 * its three transmission maps; the spread logic is written once and iterates the channels, with
 * cross-channel checks enforcing that a villager or player carries at most one disease at a time.
 * Per-disease tuning (chances, thresholds, decay) lives on the disease definition.
 */
public final class ContagionManager {
    // Below this progress a viral disease is still "switchable" — a fresh contact incubation can clean-switch
    // off it (matches DiseaseEvents.NULLIFY_THRESHOLD, kept local to avoid a cross-class dependency).
    private static final double NULLIFY_THRESHOLD = 0.05;

    // Cross-disease globals (same for every disease).
    private static final long  PLAYER_INFECTOR_TIMEOUT      = 250L;
    private static final long  VILLAGER_EXPOSURE_DECAY_RATE = 250L;
    private static final int   VILLAGER_V_CHECK_INTERVAL    = 1200;
    private static final float VILLAGER_FLU_OUTBREAK_CHANCE = 0.05f;

    // Norovirus lingering-puddle transmission (its only villager vector — it has no proximity spread).
    private static final int   VILLAGER_CLOUD_INTERVAL   = 6000;  // an infected villager rolls every 5 min
    private static final float VILLAGER_CLOUD_CHANCE     = 0.50f; // …with a 50% chance to leave a puddle
    private static final int   CLOUD_EXPOSURE_INTERVAL   = 20;    // how often villagers-in-puddles are scanned
    private static final float WINTER_NOROVIRUS_CHANCE   = 0.05f; // per-villager roll when SS winter begins

    /** Per-disease transmission state. */
    private static final class Channel {
        final ViralDiseaseDef def;
        final String group;                                            // exclusion group (mutual-exclusion scope)
        final Map<UUID, VillagerInfection> villagers = new HashMap<>(); // exposure/immunity per villager
        final Map<UUID, PlayerInfection>   players   = new HashMap<>(); // infector lock per target player
        final Map<UUID, Villager>          infected  = new HashMap<>(); // live refs for V→V

        Channel(ViralDiseaseDef def) { this.def = def; this.group = def.exclusionGroup(); }

        boolean        has(LivingEntity e)   { return def.hasAnyEffect(e); }
        ViralContagion c()                   { return def.contagion(); }
        boolean        playerContagious()    { return def.contagion().playerContagious(); }
        boolean        villagerContagious()  { return def.contagion().villagerContagious(); }
        boolean        contactContagious()   { return playerContagious() || villagerContagious(); }
    }

    private final Map<UUID, PlayerDiseaseState> states;
    private final Map<ResourceLocation, Channel> channels = new LinkedHashMap<>();
    private final Map<UUID, Villager> knownVillagers = new HashMap<>();
    private final LingeringNorovirusManager lingering;
    private final double maxContactRadius;
    private Boolean prevWinter = null; // last observed SS winter state, for once-per-onset seeding

    // Unified per-villager immunity to a whole exclusion group (cross-disease), keyed group -> (villager
    // UUID -> expiry tick). The villager analogue of PlayerDiseaseState.groupImmunity: recovering from any
    // group member immunizes against ALL members for the shared window. The per-channel
    // VillagerInfection.immunityUntil is kept as internal exposure-state bookkeeping; THIS map is the
    // cross-disease gate every infection entry point consults. In-memory only, like the per-channel
    // records (villager infection state isn't persisted — it's re-derived from carried effects on load).
    private final Map<String, Map<UUID, Long>> villagerGroupImmunity = new HashMap<>();

    public ContagionManager(Map<UUID, PlayerDiseaseState> states, LingeringNorovirusManager lingering) {
        this.states = states;
        this.lingering = lingering;
        for (DiseaseDef def : DiseaseRegistry.contagious()) {
            if (def instanceof ViralDiseaseDef v) channels.put(def.id(), new Channel(v));
        }
        double r = 0.0;
        for (Channel ch : channels.values()) {
            if (ch.contactContagious()) r = Math.max(r, ch.c().radius());
        }
        this.maxContactRadius = r;
    }

    public void onEntityJoin(Villager villager, boolean loadedFromDisk) {
        UUID vid = villager.getUUID();
        knownVillagers.put(vid, villager);
        if (loadedFromDisk) {
            for (Channel ch : channels.values()) {
                if (ch.has(villager)) ch.infected.put(vid, villager);
            }
        } else {
            // Newly spawned or bred (not loaded from disk). Babies use the baby spawn-sick chance
            // (RSV is a baby-only vector); adults use the general one.
            long now = villager.level().getGameTime();
            boolean baby = villager.isBaby();
            for (Channel ch : channels.values()) {
                if (!ch.villagerContagious()) continue;
                float chance = baby ? ch.c().villagerBabySpawnSickChance() : ch.c().villagerSpawnSickChance();
                if (chance <= 0) continue;
                if (villagerGroupImmune(vid, ch.group, now)) continue;
                if (villager.getRandom().nextFloat() < chance) {
                    markInfected(ch, villager, now, 0);
                    ch.infected.put(vid, villager);
                    break; // a villager can only spawn with one disease
                }
            }
        }
    }

    public void onPlayerLogout(UUID playerId) {
        for (Channel ch : channels.values()) {
            ch.villagers.values().removeIf(inf -> playerId.equals(inf.infectorId) && inf.immunityUntil == 0);
            ch.players.remove(playerId);
            ch.players.values().removeIf(inf -> playerId.equals(inf.infectorId));
        }
    }

    /** Called once when flu season begins — gives each loaded villager a 5% chance to catch flu. */
    public void triggerFluOutbreak(ServerLevel level) {
        Channel flu = channels.get(DiseaseRegistry.FLU);
        if (flu == null) return;
        long gameTime = level.getGameTime();
        for (Villager villager : knownVillagers.values()) {
            if (villager.isRemoved() || villager.level() != level) continue;
            if (hasGroupEffect(villager, flu.group)) continue;
            if (villagerGroupImmune(villager.getUUID(), flu.group, gameTime)) continue;
            if (level.getRandom().nextFloat() >= VILLAGER_FLU_OUTBREAK_CHANCE) continue;
            markInfected(flu, villager, gameTime, 0);
            flu.infected.put(villager.getUUID(), villager);
        }
    }

    // Villager→villager spread for every contagious disease. Iterates only known-infected sets.
    // Called once per server tick (END phase) with a non-null server from DiseaseEvents.
    public void tickServerTick(MinecraftServer server) {
        long gameTime = server.overworld().getGameTime();
        if (gameTime % 100 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                // No need to reconcile in dimensions nobody is in; onEntityJoin re-syncs on chunk load.
                if (level.players().isEmpty()) continue;
                syncInfectedVillagers(level, gameTime);
            }
        }

        if (gameTime % 6000 == 0) {
            for (Channel ch : channels.values()) {
                long staleWindow = (long) ch.c().villagerExposureThreshold() * VILLAGER_EXPOSURE_DECAY_RATE;
                ch.villagers.entrySet().removeIf(e -> {
                    VillagerInfection inf = e.getValue();
                    if (inf.immunityUntil > 0) return gameTime >= inf.immunityUntil;
                    return gameTime - inf.lastContact >= staleWindow;
                });
                ch.players.entrySet().removeIf(e -> gameTime - e.getValue().lastContact > PLAYER_INFECTOR_TIMEOUT);
            }
            villagerGroupImmunity.values().forEach(m -> m.entrySet().removeIf(e -> gameTime >= e.getValue()));
        }

        for (Channel ch : channels.values()) {
            if (ch.infected.isEmpty()) continue;
            boolean villagerSpread = ch.villagerContagious();
            boolean lingeringTrail = ch.def.id().equals(DiseaseRegistry.NOROVIRUS); // puddle vector
            Map<UUID, Villager> toAdd = null;
            Iterator<Map.Entry<UUID, Villager>> it = ch.infected.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Villager> entry = it.next();
                UUID vid = entry.getKey();
                Villager villager = entry.getValue();
                if (villager.isRemoved() || !ch.has(villager)) {
                    it.remove();
                    continue;
                }
                DiseaseParticleEmitter.tick(villager, ch.def.particle().get());
                // Norovirus: an infected villager periodically leaves a lingering puddle (the villager
                // analogue of a player's vomit) — 50% every 5 min, no symptom needed.
                if (lingeringTrail && villager.tickCount > 0
                        && villager.tickCount % VILLAGER_CLOUD_INTERVAL == 0
                        && villager.getRandom().nextFloat() < VILLAGER_CLOUD_CHANCE
                        && villager.level() instanceof ServerLevel vlevel) {
                    lingering.place(vlevel, villager.getX(), villager.getY(), villager.getZ(), gameTime);
                }
                if (!villagerSpread) continue;   // norovirus has no proximity villager→villager spread
                if (!shouldCheckVillagerSpread(villager)) continue;

                VillagerInfection infectorInf = ch.villagers.get(vid);
                int infectorGen = infectorInf != null ? infectorInf.generation : 0;
                AABB box = villager.getBoundingBox().inflate(ch.c().radius());
                List<Villager> nearby = villager.level().getEntitiesOfClass(Villager.class, box,
                    v -> v != villager && !hasGroupEffect(v, ch.group));
                for (Villager neighbor : nearby) {
                    UUID nid = neighbor.getUUID();
                    decayAllChannels(nid, gameTime);
                    if (exposedInGroup(nid, ch.group)) continue;
                    if (villagerGroupImmune(nid, ch.group, gameTime)) continue;
                    if (villager.getRandom().nextFloat() < villagerSpreadChance(ch, infectorInf)) {
                        if (infectorInf != null) infectorInf.transmissions++;
                        markInfected(ch, neighbor, gameTime, infectorGen + 1);
                        if (toAdd == null) toAdd = new HashMap<>();
                        toAdd.put(nid, neighbor); // deferred — we're iterating ch.infected
                    }
                }
            }
            if (toAdd != null) ch.infected.putAll(toAdd);
        }

        // Norovirus lingering puddles: villagers standing in one build exposure the same way they catch
        // any virus (decay → threshold → roll). Scanned on an interval to bound the entity query cost.
        Channel noro = channels.get(DiseaseRegistry.NOROVIRUS);
        if (noro != null && gameTime % CLOUD_EXPOSURE_INTERVAL == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                if (level.players().isEmpty()) continue;
                if (!lingering.hasZonesIn(level)) continue;
                for (Villager villager : knownVillagers.values()) {
                    if (villager.isRemoved() || villager.level() != level) continue;
                    if (hasGroupEffect(villager, noro.group)) continue;
                    if (lingering.isInZone(villager)) exposeVillagerToCloud(noro, villager, gameTime);
                }
            }
        }

        // Serene Seasons: a one-time 5% per-villager roll the moment winter begins.
        if (noro != null) tickWinterNorovirus(server, gameTime, noro);
    }

    /** Villager exposure from standing in a norovirus puddle — mirrors {@link #spreadPlayerToVillager}
     *  but with no infector ownership (a puddle has no owner). */
    private void exposeVillagerToCloud(Channel ch, Villager villager, long now) {
        UUID vid = villager.getUUID();
        if (villagerGroupImmune(vid, ch.group, now)) return;
        VillagerInfection inf = decayExposure(ch.villagers, vid, now);
        if (inf != null && now < inf.immunityUntil) return;
        if (inf != null && inf.immunityUntil > 0) { ch.villagers.remove(vid); inf = null; }
        if (inf == null) {
            inf = new VillagerInfection(null, now);
            ch.villagers.put(vid, inf);
        } else {
            inf.lastContact = now;
        }
        inf.exposure++;
        if (inf.exposure >= ch.c().villagerExposureThreshold()
                && villager.getRandom().nextFloat() < ch.c().playerTransmissionChance()) {
            markInfected(ch, villager, now, 1);
            ch.infected.put(vid, villager);
        }
    }

    /** Seeds norovirus into villagers when Serene Seasons winter begins — a one-time 5% per loaded
     *  overworld villager, fired only on the no-winter→winter transition. */
    private void tickWinterNorovirus(MinecraftServer server, long gameTime, Channel noro) {
        if (!SereneSeasonsCompat.LOADED) return;
        ServerLevel overworld = server.overworld();
        boolean winter = SereneSeasonsCompat.isWinter(overworld);
        boolean onset = prevWinter != null && !prevWinter && winter;
        prevWinter = winter;
        if (!onset) return;
        for (Villager villager : knownVillagers.values()) {
            if (villager.isRemoved() || villager.level() != overworld) continue;
            if (hasGroupEffect(villager, noro.group)) continue;
            if (villagerGroupImmune(villager.getUUID(), noro.group, gameTime)) continue;
            if (overworld.getRandom().nextFloat() >= WINTER_NOROVIRUS_CHANCE) continue;
            markInfected(noro, villager, gameTime, 0);
            noro.infected.put(villager.getUUID(), villager);
        }
    }

    // Player→player, player→villager, villager→player. Called from DiseaseEvents.onPlayerTick.
    public void tick(ServerPlayer player, PlayerDiseaseState data) {
        long gameTime = player.level().getGameTime();
        if (gameTime % 100 != player.getId() % 100) return;

        // Per-channel, not a blanket pathogen-wide flag: immunity is now organ+pathogen scoped, so a
        // player immune to one contact-contagious group may still be susceptible to another.
        boolean anySusceptible = false;
        for (Channel ch : channels.values()) {
            if (ch.contactContagious() && !data.isGroupImmune(ch.group, gameTime)) { anySusceptible = true; break; }
        }
        if (!anySusceptible && !isPlayerInfectious(player, data)) return;

        // The disease (if any) the player is in infectious contact with AND susceptible to this check —
        // drives contact incubation below. Stays null when nothing relevant is in range.
        ResourceLocation contactDisease = null;

        if (maxContactRadius > 0.0) {
            AABB box = player.getBoundingBox().inflate(maxContactRadius);
            List<ServerPlayer> nearbyPlayers = player.level().getEntitiesOfClass(
                ServerPlayer.class, box, e -> e != player);
            List<Villager> nearbyVillagers = player.level().getEntitiesOfClass(
                Villager.class, box, v -> true);
            if (!nearbyPlayers.isEmpty() || !nearbyVillagers.isEmpty()) {
                for (ServerPlayer target : nearbyPlayers) {
                    contactDisease = processContactTarget(player, data, target, gameTime, contactDisease);
                }
                for (Villager target : nearbyVillagers) {
                    contactDisease = processContactTarget(player, data, target, gameTime, contactDisease);
                }
            }
        }

        handleContactIncubation(player, data, contactDisease, gameTime);
    }

    private boolean isPlayerInfectious(ServerPlayer player, PlayerDiseaseState data) {
        for (Channel ch : channels.values()) {
            ResourceLocation id = ch.def.id();
            if (data.progress(id) > 0.0 || data.inRecovery(id) || ch.has(player)
                    || data.hasActiveComplication(ch.group)) return true;
        }
        return false;
    }

    private ResourceLocation processContactTarget(ServerPlayer player, PlayerDiseaseState data, LivingEntity target,
                                                  long gameTime, ResourceLocation contactDisease) {
        for (Channel ch : channels.values()) {
            ResourceLocation id = ch.def.id();
            // A latched complication spreads the disease that caused it, even though the player
            // carries the complication effect rather than the source's effect.
            boolean viaComplication = activeComplicationHasSource(data, id);
            boolean infectious = data.inRecovery(id) || ch.has(player) || viaComplication;

            if (infectious) {
                if (ch.playerContagious() && target instanceof ServerPlayer other) {
                    spreadPlayerToPlayer(player, ch, other, gameTime);
                } else if (ch.villagerContagious() && target instanceof Villager villager
                        && !hasGroupEffect(villager, ch.group)) {
                    spreadPlayerToVillager(player, ch, villager, gameTime);
                }
            }

            // --- Villager spreading to this player (the existing flat-bump accumulation) ---
            ResourceLocation pendingIncubId = data.getPendingIncubationId();
            if (ch.villagerContagious() && target instanceof Villager villager && ch.has(villager)
                    && !otherSameGroupActive(data, ch)
                    && (pendingIncubId == null || id.equals(pendingIncubId))
                    && data.groupImmunityUntil(ch.group) <= gameTime
                    && player.getRandom().nextFloat() < ch.c().playerTransmissionChance()) {
                data.addProgress(id, ch.c().transmissionBump() * ImmuneManager.getContagionMultiplier(player));
            }

            // --- Unified contact-incubation detection (receiver side; covers player/villager contact) ---
            if (contactDisease == null && !data.isGroupImmune(ch.group, gameTime) && ch.contactContagious()
                    && susceptibleToContact(data, id, ch.group)
                    && isInfectiousContactSource(target, ch, id)) {
                contactDisease = id;
            }
        }
        return contactDisease;
    }

    /** Whether the player can receive a fresh contact incubation of {@code id}: not already latched with it, not
     *  occupied by an active complication in the same group (pneumonia, …), and either nothing else in the
     *  group is in progress or only a still-switchable sub-threshold rival. */
    private boolean susceptibleToContact(PlayerDiseaseState data, ResourceLocation id, String group) {
        if (data.inRecovery(id)) return false;
        if (data.hasActiveComplication(group)) return false;
        ResourceLocation rival = data.activeInGroup(group);
        return rival == null || rival.equals(id) || data.progress(rival) < NULLIFY_THRESHOLD;
    }

    /** Whether {@code target} is an infectious source of {@code id} from a receiver's view — a latched
     *  player/villager carrying the disease (or a pneumonia carrier whose source IS this disease). */
    private boolean isInfectiousContactSource(LivingEntity target, Channel ch, ResourceLocation id) {
        if (target instanceof ServerPlayer other) {
            return ch.playerContagious()
                    && (ch.has(other) || DiseaseEffects.hasComplicationVariantForSource(other, id));
        }
        if (target instanceof Villager villager) {
            return ch.villagerContagious() && ch.has(villager);
        }
        return false;
    }

    /** Contact-incubation roll: while infectious contact is present and no incubation is in flight,
     *  commit a one-shot incubation for the contacted disease, drawn from that disease's def range.
     *  The caller already runs on the staggered contact scan cadence, so this can refresh sustained
     *  exposure without adding per-tick proximity work. */
    private void handleContactIncubation(ServerPlayer player, PlayerDiseaseState data, ResourceLocation contactDisease, long gameTime) {
        if (contactDisease != null && data.getPendingIncubation() <= 0.0
                && DiseaseRegistry.get(contactDisease) instanceof ViralDiseaseDef vdef
                && data.canStartNewDisease(contactDisease)) {
            data.setPendingIncubation(vdef.rollIncubation(player.getRandom(), ImmuneManager.isImmunodeficient(player)), contactDisease);
        }
    }

    private void spreadPlayerToPlayer(ServerPlayer player, Channel ch, ServerPlayer other, long gameTime) {
        ResourceLocation id = ch.def.id();
        PlayerDiseaseState otherData = getOrCreate(other);
        if (otherData.groupImmunityUntil(ch.group) > gameTime || otherSameGroupActive(otherData, ch)) return;

        UUID tid = other.getUUID();
        PlayerInfection pinf = ch.players.get(tid);
        boolean canInfect = pinf == null
                || pinf.infectorId.equals(player.getUUID())
                || gameTime - pinf.lastContact > PLAYER_INFECTOR_TIMEOUT;
        if (!canInfect) return;

        if (pinf != null && pinf.infectorId.equals(player.getUUID())) {
            pinf.lastContact = gameTime;
        } else {
            ch.players.put(tid, new PlayerInfection(player.getUUID(), gameTime));
        }
        if (player.getRandom().nextFloat() < ch.c().playerTransmissionChance()) {
            otherData.addProgress(id, ch.c().transmissionBump() * ImmuneManager.getContagionMultiplier(other));
        }
    }

    private boolean activeComplicationHasSource(PlayerDiseaseState data, ResourceLocation sourceId) {
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (data.inRecovery(def.id()) && sourceId.equals(data.complicationSource(def.id()))) return true;
        }
        return false;
    }

    private void spreadPlayerToVillager(ServerPlayer player, Channel ch, Villager villager, long now) {
        UUID vid = villager.getUUID();
        if (villagerGroupImmune(vid, ch.group, now)) return;
        decayAllChannels(vid, now);
        if (exposedInOtherChannel(ch, vid)) return;
        VillagerInfection inf = decayExposure(ch.villagers, vid, now);
        if (inf != null && now < inf.immunityUntil) return;
        if (inf != null && inf.immunityUntil > 0) { ch.villagers.remove(vid); inf = null; }
        if (inf != null && inf.infectorId != null && !inf.infectorId.equals(player.getUUID())) return;
        if (inf == null) {
            inf = new VillagerInfection(player.getUUID(), now);
            ch.villagers.put(vid, inf);
        } else {
            inf.lastContact = now;
        }
        inf.exposure++;
        if (inf.exposure >= ch.c().villagerExposureThreshold()
                && player.getRandom().nextFloat() < ch.c().playerTransmissionChance()) {
            markInfected(ch, villager, now, 1);
            ch.infected.put(vid, villager);
        }
    }

    private void syncInfectedVillagers(ServerLevel level, long gameTime) {
        boolean needsReconcile = false;
        for (Channel ch : channels.values()) {
            if (!ch.villagerContagious()) continue;
            for (Villager v : ch.infected.values()) {
                if (!v.isRemoved() && v.level() == level) { needsReconcile = true; break; }
            }
            if (needsReconcile) break;
        }

        Iterator<Map.Entry<UUID, Villager>> it = knownVillagers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Villager> entry = it.next();
            Villager villager = entry.getValue();
            if (villager.isRemoved()) {
                UUID vid = entry.getKey();
                it.remove();
                for (Channel ch : channels.values()) ch.infected.remove(vid);
                continue;
            }
            if (villager.level() != level) continue;
            if (!needsReconcile) continue;
            UUID vid = villager.getUUID();
            Channel active = null;
            for (Channel ch : channels.values()) {
                if (!ch.villagerContagious()) continue;   // player-only diseases aren't tracked on villagers
                MobEffectInstance eff = ch.def.activeEffect(villager);
                if (eff == null) continue;
                active = ch;
                ch.infected.put(vid, villager);
                VillagerInfection inf = ch.villagers.computeIfAbsent(vid, k -> new VillagerInfection(null, gameTime));
                if (inf.immunityUntil <= gameTime && eff.getDuration() > 0) {
                    inf.immunityUntil = gameTime + eff.getDuration() + ch.c().villagerImmunityTicks();
                    grantVillagerGroupImmunity(vid, ch.group, gameTime + eff.getDuration() + DiseaseRegistry.VIRAL_IMMUNITY_TICKS);
                }
            }
            if (active != null) clearOtherChannels(active, vid);
        }
    }

    /** Effect + cross-channel clear + fresh infection record. Does NOT add to {@code ch.infected}
     *  (callers do, so V→V can defer the put while iterating that map). The tier is rolled per
     *  infection with the same weighting players use, so villagers carry varied severities too. */
    private void markInfected(Channel ch, Villager villager, long now, int generation) {
        UUID vid = villager.getUUID();
        Severity sev = Severity.rollWeighted(ch.def.tiers(), villager.getRandom());
        villager.addEffect(new MobEffectInstance(ch.def.effectFor(sev).get(), ch.c().villagerEffectTicks(), 0, false, false, true));
        clearOtherChannels(ch, vid);
        VillagerInfection inf = ch.villagers.computeIfAbsent(vid, k -> new VillagerInfection(null, 0));
        inf.immunityUntil = now + ch.c().villagerEffectTicks() + ch.c().villagerImmunityTicks();
        inf.exposure      = 0;
        inf.infectorId    = null;
        inf.lastContact   = 0;
        inf.generation    = generation;
        inf.transmissions = 0;
        // Unified cross-disease window: effect duration + the shared post-recovery immunity (mirrors a
        // player getting exclusion-group immunity on recovery). Blocks catching ANY group member meanwhile.
        grantVillagerGroupImmunity(vid, ch.group, now + ch.c().villagerEffectTicks() + DiseaseRegistry.VIRAL_IMMUNITY_TICKS);
    }

    /** Clears a villager from every OTHER channel in the SAME exclusion group — enforces one disease
     *  per group while letting a different group (e.g. norovirus) coexist with a respiratory one. */
    private void clearOtherChannels(Channel self, UUID vid) {
        for (Channel ch : channels.values()) {
            if (ch == self || !ch.group.equals(self.group)) continue;
            ch.villagers.remove(vid);
            ch.infected.remove(vid);
        }
    }

    /** Whether the villager carries any contagious effect within {@code group}. */
    private boolean hasGroupEffect(Villager villager, String group) {
        for (Channel ch : channels.values()) {
            if (ch.group.equals(group) && ch.has(villager)) return true;
        }
        return false;
    }

    /** Whether a disease other than this channel's, in the SAME exclusion group, is active on the player —
     *  including an active viral complication (pneumonia, …), which also occupies the group. */
    private boolean otherSameGroupActive(PlayerDiseaseState data, Channel self) {
        for (Channel ch : channels.values()) {
            if (ch == self || !ch.group.equals(self.group)) continue;
            ResourceLocation id = ch.def.id();
            if (data.progress(id) > 0 || data.inRecovery(id)) return true;
        }
        return data.hasActiveComplication(self.group);
    }

    private void decayAllChannels(UUID villagerId, long now) {
        for (Channel ch : channels.values()) decayExposure(ch.villagers, villagerId, now);
    }

    private boolean exposedInGroup(UUID villagerId, String group) {
        for (Channel ch : channels.values()) {
            if (ch.group.equals(group) && hasActiveExposure(ch.villagers, villagerId)) return true;
        }
        return false;
    }

    private boolean exposedInOtherChannel(Channel self, UUID villagerId) {
        for (Channel ch : channels.values()) {
            if (ch != self && ch.group.equals(self.group) && hasActiveExposure(ch.villagers, villagerId)) return true;
        }
        return false;
    }

    private boolean shouldCheckVillagerSpread(Villager villager) {
        return villager.tickCount > 0 && villager.tickCount % VILLAGER_V_CHECK_INTERVAL == 0;
    }

    private float villagerSpreadChance(Channel ch, VillagerInfection infectorInf) {
        int generation    = infectorInf != null ? infectorInf.generation : 0;
        int transmissions = infectorInf != null ? infectorInf.transmissions : 0;
        ViralContagion c = ch.c();
        return c.villagerVChance()
                * (float) Math.pow(c.generationDecay(), generation)
                * (float) Math.pow(c.transmissionDecay(), transmissions);
    }

    /** Whether the villager currently holds unified immunity to {@code group} (blocks every member). */
    private boolean villagerGroupImmune(UUID vid, String group, long gameTime) {
        Map<UUID, Long> m = villagerGroupImmunity.get(group);
        Long until = m == null ? null : m.get(vid);
        return until != null && until > gameTime;
    }

    /** Arm a villager's shared group-immunity window to the given absolute expiry (never shortening). */
    private void grantVillagerGroupImmunity(UUID vid, String group, long until) {
        villagerGroupImmunity.computeIfAbsent(group, g -> new HashMap<>()).merge(vid, until, Math::max);
    }

    private boolean hasActiveExposure(Map<UUID, VillagerInfection> infections, UUID villagerId) {
        VillagerInfection inf = infections.get(villagerId);
        return inf != null && inf.immunityUntil == 0 && inf.exposure > 0;
    }

    private VillagerInfection decayExposure(Map<UUID, VillagerInfection> infections, UUID villagerId, long now) {
        VillagerInfection inf = infections.get(villagerId);
        if (inf == null || inf.immunityUntil > 0) return inf;
        int decay = (int) ((now - inf.lastContact) / VILLAGER_EXPOSURE_DECAY_RATE);
        if (decay <= 0) return inf;
        inf.exposure = Math.max(0, inf.exposure - decay);
        inf.lastContact = now;
        if (inf.exposure == 0) {
            infections.remove(villagerId);
            return null;
        }
        return inf;
    }

    public PlayerDiseaseState getOrCreate(ServerPlayer player) {
        return states.computeIfAbsent(player.getUUID(), uuid -> {
            CompoundTag root = player.getPersistentData().getCompound(SimpleDiseases.MOD_ID);
            return PlayerDiseaseState.loadFromNbt(root);
        });
    }

    public VillagerInfection getVillagerInfection(ResourceLocation diseaseId, UUID vid) {
        Channel ch = channels.get(diseaseId);
        return ch == null ? null : ch.villagers.get(vid);
    }
}
