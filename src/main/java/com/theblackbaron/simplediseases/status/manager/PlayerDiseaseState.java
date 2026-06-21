package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.status.component.Components;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.component.ImmunityComponent;
import com.theblackbaron.simplediseases.status.component.ProgressComponent;
import com.theblackbaron.simplediseases.status.component.SourceComponent;
import com.theblackbaron.simplediseases.status.component.SymptomPoolComponent;
import com.theblackbaron.simplediseases.status.def.BacterialDiseaseDef;
import com.theblackbaron.simplediseases.status.def.ComplicationDiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralized per-player disease state. Shared player-level environment (wetness, transient
 * windchill exposure) plus a registry-id-keyed map of {@link DiseaseInstance}s, each carrying the
 * component bag its category declares. Replaces the old hand-rolled per-disease field set; new
 * categories/diseases add instances and components without touching this class.
 */
public class PlayerDiseaseState {
    private static final String KEY_WET        = "wet";
    private static final String KEY_DISEASES   = "diseases";
    private static final String KEY_GROUP_IMM  = "groupImmunity";
    private static final String KEY_INCUBATION        = "pendingIncubation";
    private static final String KEY_INCUBATION_ID     = "pendingIncubationId";
    private static final String KEY_WAS_WATER   = "wasInInfectedWater";
    private static final String KEY_INJURY      = "injury";
    private static final String KEY_ACCUM_FATIGUE_STREAK = "accumFatigueStreak";
    private static final String KEY_FATIGUE_DEFICIENCY   = "fatigueDeficiency";
    private static final String KEY_ACCUM_FATIGUE_WARNED = "accumFatigueWarned";

    // Legacy (pre-component) NBT keys, for migrating existing worlds.
    private static final String L_WET = "sd_wet";

    private double wetProgress            = 0.0; // [0.0, 1.0]
    private int    windchillExposureTicks = 0;   // transient; reset on exposure break/death

    // Unified post-recovery immunity windows keyed by exclusion group (e.g. "viral"): game-time tick at
    // which immunity to the whole group expires. Recovering from any member disease (incl. pneumonia)
    // arms the shared window; all fresh-acquisition paths for that group consult it. Replaces the
    // per-disease ImmunityComponent on the player path. (Villager immunity is separate.)
    private final Map<String, Long> groupImmunity = new HashMap<>();

    // Unified "committed incubation on exposure" (see DiseaseEvents + ContagionManager). An exposure event —
    // entering infected water (norovirus) OR coming into contact with an infectious player/villager
    // (cold/flu/rsv via P→P / V→P) — rolls a one-shot incubation that then bleeds into THAT disease's progress
    // over time, even after the exposure ends, until spent. pendingIncubation is the remaining budget (0 = none,
    // and IS the non-stacking guard: a new incubation only rolls while this is 0); pendingIncubationId is which disease
    // it feeds. Because the viral group is mutually exclusive, only one incubation is ever in flight. The two
    // water edge flag tracks last-sample membership for rising-edge detection of reservoir/puddle exposure. Persisted.
    private double           pendingIncubation          = 0.0;
    private ResourceLocation pendingIncubationId        = null;
    private boolean          wasInInfectedWater   = false; // water-reservoir/puddle edge (per tick)

    // Transient cache of the last waterborne-norovirus verdict (see WaterborneManager) — avoids
    // re-hashing the infected-region test every tick while swimming. Not persisted.
    private long    lastWaterRegion   = Long.MIN_VALUE;
    private long    lastWaterEpoch    = Long.MIN_VALUE;
    private boolean lastWaterWinter   = false;
    private boolean lastWaterInfected = false;

    private final Map<ResourceLocation, DiseaseInstance> diseases = new HashMap<>();
    private PlayerInjuryState injury = new PlayerInjuryState();

    private long  accumFatigueStreakTicks = 0L;
    private boolean fatigueDeficiencyActive = false;
    private boolean accumFatigueWarned      = false;

    public long  getAccumFatigueStreakTicks()  { return accumFatigueStreakTicks; }
    public void  setAccumFatigueStreakTicks(long v) { accumFatigueStreakTicks = Math.max(0L, v); }
    public boolean isFatigueDeficiencyActive() { return fatigueDeficiencyActive; }
    public void  setFatigueDeficiencyActive(boolean v) { fatigueDeficiencyActive = v; }
    public boolean isAccumFatigueWarned()    { return accumFatigueWarned; }
    public void  setAccumFatigueWarned(boolean v) { accumFatigueWarned = v; }

    // --- Environment ---

    public double getWetProgress() { return wetProgress; }

    public void addWetProgress(double value) {
        if (Double.isNaN(value)) return;
        wetProgress = Math.max(0.0, Math.min(1.0, wetProgress + value));
    }

    public int  getWindchillExposureTicks()     { return windchillExposureTicks; }
    public void setWindchillExposureTicks(int t) { windchillExposureTicks = t; }

    public long    getLastWaterRegion()  { return lastWaterRegion; }
    public long    getLastWaterEpoch()   { return lastWaterEpoch; }
    public boolean isLastWaterWinter()   { return lastWaterWinter; }
    public boolean isLastWaterInfected() { return lastWaterInfected; }

    public void cacheWaterVerdict(long region, long epoch, boolean winter, boolean infected) {
        lastWaterRegion   = region;
        lastWaterEpoch    = epoch;
        lastWaterWinter   = winter;
        lastWaterInfected = infected;
    }

    // --- Unified committed incubation ---

    public double           getPendingIncubation()   { return pendingIncubation; }
    public ResourceLocation getPendingIncubationId() { return pendingIncubationId; }

    /** Commit (or top up) an incubation targeting a specific disease. */
    public void setPendingIncubation(double incubation, ResourceLocation id) {
        pendingIncubation   = Math.max(0.0, incubation);
        pendingIncubationId = pendingIncubation > 0.0 ? id : null;
    }

    public void clearPendingIncubation() { pendingIncubation = 0.0; pendingIncubationId = null; }

    public boolean wasInInfectedWater()             { return wasInInfectedWater; }
    public void    setWasInInfectedWater(boolean v) { wasInInfectedWater = v; }

    // --- Injury state ---

    public PlayerInjuryState injury() { return injury; }

    // --- Disease instances ---

    /** The instance for a disease, creating it (with its category's components) if absent. */
    public DiseaseInstance getOrCreate(ResourceLocation id) {
        return diseases.computeIfAbsent(id, k -> {
            DiseaseDef def = DiseaseRegistry.get(k);
            if (def == null) return null;
            return new DiseaseInstance(k, def.category().componentTypes());
        });
    }

    public DiseaseInstance peek(ResourceLocation id)   { return diseases.get(id); }
    public Collection<DiseaseInstance> instances()     { return diseases.values(); }

    // --- Progress/immunity convenience (for diseases carrying these components) ---

    public double progress(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return 0.0;
        ProgressComponent p = inst.get(Components.PROGRESS);
        return p == null ? 0.0 : p.progress;
    }

    public boolean inRecovery(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return false;
        ProgressComponent p = inst.get(Components.PROGRESS);
        return p != null && p.inRecovery;
    }

    public long immunityUntil(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return 0L;
        ImmunityComponent im = inst.get(Components.IMMUNITY);
        return im == null ? 0L : im.immunityUntil;
    }

    // --- Unified group immunity (shared post-recovery window across an exclusion group) ---

    /** Game-time tick at which immunity to the given exclusion group expires (0 = none/never). */
    public long groupImmunityUntil(String group) {
        Long until = groupImmunity.get(group);
        return until == null ? 0L : until;
    }

    public boolean isGroupImmune(String group, long gameTime) {
        return groupImmunityUntil(group) > gameTime;
    }

    /** Arm the shared immunity window for an exclusion group, never shortening an existing longer one. */
    public void grantGroupImmunity(String group, long gameTime, long ticks) {
        groupImmunity.merge(group, gameTime + ticks, Math::max);
    }

    public long nextEpisodeAt(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return 0L;
        SymptomPoolComponent sp = inst.get(Components.SYMPTOMS);
        return sp == null ? 0L : sp.nextEpisodeAt;
    }

    public int symptomCount(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return 0;
        SymptomPoolComponent sp = inst.get(Components.SYMPTOMS);
        return sp == null ? 0 : sp.count();
    }

    public com.theblackbaron.simplediseases.status.def.Severity tierOf(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return null;
        com.theblackbaron.simplediseases.status.component.TierComponent t = inst.get(Components.TIER);
        return t == null ? null : t.severity();
    }

    /** Whether the player currently has any active (developing or latched) complication-category disease
     *  in the given exclusion group — pneumonia today, any future complication tomorrow. A complication
     *  occupies its group while active, so fresh acquisition of group members must be blocked. Generic
     *  over {@link DiseaseRegistry#complications()} rather than hardcoding pneumonia. */
    public boolean hasActiveComplication(String group) {
        for (DiseaseDef def : DiseaseRegistry.complications()) {
            if (group.equals(def.exclusionGroup())
                    && (progress(def.id()) > 0.0 || inRecovery(def.id()))) {
                return true;
            }
        }
        return false;
    }

    /** The source disease of a viral complication (pneumonia) on this player, or null if it has none. */
    public ResourceLocation complicationSource(ResourceLocation complicationId) {
        DiseaseInstance inst = diseases.get(complicationId);
        if (inst == null) return null;
        SourceComponent s = inst.get(Components.SOURCE);
        return s == null ? null : s.sourceId;
    }

    /** Zeroes a disease's accumulation (progress + recovery flag). Used for the reservoir "clean switch"
     *  that wipes a still-uncommitted (sub-threshold) viral disease so norovirus can take over. Only
     *  meaningful below the first symptom threshold, where no tier/pool/immunity has been established. */
    public void clearProgress(ResourceLocation id) {
        DiseaseInstance inst = diseases.get(id);
        if (inst == null) return;
        ProgressComponent p = inst.get(Components.PROGRESS);
        if (p != null) { p.progress = 0.0; p.inRecovery = false; }
    }

    /** Clamp-adds progress, looking up the cap from the disease definition. */
    public void addProgress(ResourceLocation id, double delta) {
        DiseaseInstance inst = getOrCreate(id);
        if (inst == null) return;
        ProgressComponent p = inst.get(Components.PROGRESS);
        if (p == null) return;
        DiseaseDef def = DiseaseRegistry.get(id);
        double cap = def instanceof ViralDiseaseDef v ? v.progressCap()
                   : def instanceof ComplicationDiseaseDef c ? c.progressCap()
                   : def instanceof BacterialDiseaseDef b ? b.progressCap()
                   : Double.MAX_VALUE;
        p.add(delta, cap);
    }

    /** Debug helper: seed a complication's source (so {@code /sdaccumulate pneumonia} can latch standalone
     *  without an actual qualifying disease). No-op if a source is already set. */
    public void debugSetComplicationSource(ResourceLocation complicationId, ResourceLocation sourceId, long latchTicks) {
        DiseaseInstance inst = getOrCreate(complicationId);
        if (inst == null) return;
        SourceComponent s = inst.get(Components.SOURCE);
        if (s != null && !s.hasSource()) { s.sourceId = sourceId; s.latchTicks = latchTicks; }
    }

    // --- Persistence ---

    public void saveToNbt(CompoundTag root) {
        root.putDouble(KEY_WET, wetProgress);
        ListTag list = new ListTag();
        for (DiseaseInstance inst : diseases.values()) list.add(inst.save());
        root.put(KEY_DISEASES, list);
        CompoundTag gi = new CompoundTag();
        for (Map.Entry<String, Long> e : groupImmunity.entrySet()) gi.putLong(e.getKey(), e.getValue());
        root.put(KEY_GROUP_IMM, gi);
        root.putDouble(KEY_INCUBATION, pendingIncubation);
        if (pendingIncubationId != null) root.putString(KEY_INCUBATION_ID, pendingIncubationId.toString());
        root.putBoolean(KEY_WAS_WATER, wasInInfectedWater);
        if (injury.hasActiveInjury()) root.put(KEY_INJURY, injury.save());
        root.putLong(KEY_ACCUM_FATIGUE_STREAK, accumFatigueStreakTicks);
        root.putBoolean(KEY_FATIGUE_DEFICIENCY, fatigueDeficiencyActive);
        root.putBoolean(KEY_ACCUM_FATIGUE_WARNED, accumFatigueWarned);
    }

    public static PlayerDiseaseState loadFromNbt(CompoundTag root) {
        PlayerDiseaseState state = new PlayerDiseaseState();
        if (root.contains(KEY_DISEASES, Tag.TAG_LIST)) {
            state.wetProgress = root.getDouble(KEY_WET);
            ListTag list = root.getList(KEY_DISEASES, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag t = list.getCompound(i);
                ResourceLocation id = ResourceLocation.tryParse(t.getString("id"));
                if (id == null) continue; // malformed id — skip rather than crash
                DiseaseDef def = DiseaseRegistry.get(id);
                if (def == null) continue; // unknown/removed disease — skip rather than corrupt
                state.diseases.put(id, DiseaseInstance.load(t, def.category().componentTypes()));
            }
        } else if (root.contains(L_WET) || root.contains("sd_cold") || root.contains("sd_flu")) {
            state.migrateLegacy(root);
        }
        if (root.contains(KEY_GROUP_IMM, Tag.TAG_COMPOUND)) {
            CompoundTag gi = root.getCompound(KEY_GROUP_IMM);
            for (String k : gi.getAllKeys()) state.groupImmunity.put(k, gi.getLong(k));
        }
        state.pendingIncubation = root.getDouble(KEY_INCUBATION);
        if (root.contains(KEY_INCUBATION_ID)) {
            state.pendingIncubationId = ResourceLocation.tryParse(root.getString(KEY_INCUBATION_ID));
        }
        state.wasInInfectedWater   = root.getBoolean(KEY_WAS_WATER);    // false if absent
        if (root.contains(KEY_INJURY, Tag.TAG_COMPOUND)) {
            state.injury = PlayerInjuryState.load(root.getCompound(KEY_INJURY));
        }
        state.accumFatigueStreakTicks  = root.getLong(KEY_ACCUM_FATIGUE_STREAK);
        state.fatigueDeficiencyActive  = root.getBoolean(KEY_FATIGUE_DEFICIENCY);
        state.accumFatigueWarned       = root.getBoolean(KEY_ACCUM_FATIGUE_WARNED);
        return state;
    }

    private void migrateLegacy(CompoundTag t) {
        wetProgress = t.getDouble(L_WET);
        migrateViral(t, DiseaseRegistry.COLD, "sd_cold", "sd_recovery",     "sd_symptoms",     "sd_cold_immunity", "sd_cold_next_episode");
        migrateViral(t, DiseaseRegistry.FLU,  "sd_flu",  "sd_flu_recovery", "sd_flu_symptoms", "sd_flu_immunity",  "sd_flu_next_episode");
    }

    private void migrateViral(CompoundTag t, ResourceLocation id,
                              String kProgress, String kRecovery, String kSymptoms, String kImmunity, String kNext) {
        double  prog = t.getDouble(kProgress);
        boolean rec  = t.getBoolean(kRecovery);
        int     mask = t.getInt(kSymptoms);
        long    imm  = t.getLong(kImmunity);
        long    next = t.getLong(kNext);
        if (prog <= 0 && !rec && mask == 0 && imm == 0 && next == 0) return; // nothing worth migrating
        DiseaseInstance inst = getOrCreate(id);
        ProgressComponent p = inst.get(Components.PROGRESS);  p.progress = prog; p.inRecovery = rec;
        ImmunityComponent im = inst.get(Components.IMMUNITY); im.immunityUntil = imm;
        SymptomPoolComponent sp = inst.get(Components.SYMPTOMS); sp.mask = mask; sp.nextEpisodeAt = next;
    }

    public PlayerDiseaseState copy() {
        PlayerDiseaseState c = new PlayerDiseaseState();
        c.wetProgress = wetProgress;
        for (Map.Entry<ResourceLocation, DiseaseInstance> e : diseases.entrySet()) {
            DiseaseDef def = DiseaseRegistry.get(e.getKey());
            if (def == null) continue;
            c.diseases.put(e.getKey(), DiseaseInstance.load(e.getValue().save(), def.category().componentTypes()));
        }
        c.groupImmunity.putAll(groupImmunity);
        c.pendingIncubation          = pendingIncubation;
        c.pendingIncubationId        = pendingIncubationId;
        c.wasInInfectedWater   = wasInInfectedWater;
        c.injury = injury.copy();
        c.accumFatigueStreakTicks  = accumFatigueStreakTicks;
        c.fatigueDeficiencyActive  = fatigueDeficiencyActive;
        c.accumFatigueWarned       = accumFatigueWarned;
        return c;
    }

    public void resetOnDeath() {
        wetProgress            = 0.0;
        windchillExposureTicks = 0;
        diseases.clear();
        groupImmunity.clear();
        pendingIncubation            = 0.0;
        pendingIncubationId          = null;
        wasInInfectedWater     = false;
        injury.reset();
        accumFatigueStreakTicks  = 0L;
        fatigueDeficiencyActive  = false;
        accumFatigueWarned       = false;
    }
}
