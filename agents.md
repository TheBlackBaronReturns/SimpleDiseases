# agents.md — SimpleDiseases Developer Reference

**Mod:** Simple Diseases · **ID:** `simplediseases` · **Version:** 0.1.0  
**Stack:** Minecraft 1.20.1 · Forge 47.4.10 · Parchment 2023.09.03-1.20.1 · Java 17  
**Author:** The Black Baron · **License:** MIT  
**Repository:** https://github.com/TheBlackBaronReturns/SimpleDiseases

---

## Build Commands

```bash
./gradlew build      # Compile and package JAR
./gradlew runClient  # Launch client with mod loaded
./gradlew runServer  # Launch dedicated server
./gradlew runData    # Regenerate data assets
```

No test framework. Verify features in-client.

---

## Development Principles

**After correctness, the highest priorities for every change are performance, multiplayer compatibility, cross-mod compatibility across the Forge ecosystem, and code optimization.** Simple Diseases is built for **100+ mod modpacks** — every feature must coexist with unknown mods without breaking them. Treat these as hard constraints, not nice-to-haves.

| Principle | Expectation |
|---|---|
| **Performance** | Server-authoritative tick logic; precompute per-tick values once (e.g. recovery multipliers in `DiseaseEvents`); reuse collection caches; avoid per-player allocations on hot paths; prefer primitives and in-place mutation over new objects each tick. |
| **Multiplayer** | Gameplay state lives on the server and syncs through vanilla channels (`MobEffectInstance`, NBT persistence) or minimal custom packets. World feedback uses `ServerLevel.sendParticles()` so nearby players see vomit, blood, cough, and ambient disease particles. Reserve `PacketDistributor.PLAYER` for **local HUD only** (e.g. `BleedingSplatterPacket`). Body shiver renders on all clients via synced effects + `LivingEntityRendererMixin`. |
| **Cross-mod compatibility** | **Forge-first, non-invasive.** Prefer existing Forge and vanilla extension points before writing new infrastructure. Use `DeferredRegister`, the Forge event bus, `MobEffect` / attributes, tags, `SavedData`, and overlay events (`RenderGuiOverlayEvent`) over mixins, custom packets, or core-class patches. Keep mixins **narrow and few** — inject only where Forge/vanilla has no hook; use `require = 0` and `SimpleDiseasesMixinPlugin` for optional targets (JEED). Never call optional-mod classes outside `compat/`; detect at runtime (`ModList`, `ColdSweatCompat.LOADED`) and **fail open** with offline fallbacks. Do not cancel, replace, or globally override other mods' behavior. Avoid hard dependencies, static hooks on foreign classes, and broad `@Overwrite`s. Scope registrations to `simplediseases:` IDs. When a Forge API solves the problem, use it — do not reinvent it. |
| **Code optimization** | Prefer **data-driven** definitions (`DiseaseRegistry.bootstrap()`, `SymptomConfig`, component bags) over scattered per-disease logic. **Centralize** shared behavior (`ColdSweatCompat`, `WorseningRoll`, `SymptomService`, `DiseaseContext`) instead of duplicating it across categories. **Remove redundancies** when consolidating. Keep modules **small, reusable, and composable** — one manager/service per concern, categories consume context rather than re-deriving state. |

When a feature conflicts with these principles, redesign the feature — do not bolt on client-only state, hard dependencies, or invasive hooks.

### Cross-mod decision order

When implementing a feature, try approaches in this order:

1. **Vanilla / Forge APIs** — effects, attributes, events, particles, sounds, data components, tags.
2. **Forge client overlays** — `RenderGuiOverlayEvent`, `VanillaGuiOverlay`, `ForgeGui` methods (before mixins).
3. **Targeted mixin** — single method, `@Inject` only, own-mod or documented vanilla hook; optional mixins gated behind mod detection.
4. **Custom packet / new subsystem** — last resort; justify why vanilla sync or Forge events are insufficient.

---

## Architecture Overview

ECS-lite disease model: `PlayerDiseaseState` holds per-disease `DiseaseInstance` component bags, plus wetness, incubation, group immunity, accumulation-fatigue streak, and `PlayerInjuryState`. Categories (`ViralCategory`, `BacterialCategory`, `ComplicationCategory`) tick via `DiseaseContext` built each tick in `DiseaseEvents`.

```
com.theblackbaron.simplediseases
├── SimpleDiseases.java           — @Mod root; registers + event buses
├── client/                       — Particles, HUD overlays, debug overlay, tooltips
├── command/SdCommands.java       — /sd* debug and admin commands
├── compat/                       — Cold Sweat + Serene Seasons (never call mods elsewhere)
├── event/                        — DiseaseEvents, CureEvents, SymptomEvents
├── mixin/                        — JEED tooltips, HUD shake/tint, sprint hunger, shared icons, sort order
├── network/                      — BleedingSplatterPacket, DebugOverlayPacket, NetworkHandler
├── particle/                     — DiseaseParticles registry + DiseaseParticleEmitter
├── sound/DiseaseSounds.java
└── status/                       — Effects, attributes, defs, managers, services
    ├── category/                 — DiseaseContext, Viral/Bacterial/Complication categories
    ├── def/                      — DiseaseRegistry, WorseningRoll, PainProfile, ConditionType
    └── manager/                  — Contagion, Wetness, Injury, FluSeason, AccumFatigue, …
```

**Wiring (`SimpleDiseases` constructor):** `DiseaseAttributes` → `DiseaseEffects` → `DiseaseParticles` / `DiseaseSounds` → `DiseaseRegistry.bootstrap()` → Forge event handlers.

### Mixins (`simplediseases.mixins.json`)

| Mixin | Side | Role |
|---|---|---|
| `PlayerMixin` | common | Double sprint food exhaustion during tachycardia |
| `GuiMixin` | client | Tachycardia low-health heart shake |
| `ForgeGuiMixin` | client | Stomach cramps red food-bar tint (`renderFood`; after `setupOverlayRenderState`) |
| `LivingEntityRendererMixin` | client | Fever/septic body shiver |
| `MobEffectTextureManagerMixin` | client | Shared per-disease HUD icons |
| `EffectRendererMixin` | client | JEED disease tooltip post-process (`require = 0`; gated by `SimpleDiseasesMixinPlugin`) |
| `ScreenExtensionsHandlerMixin` | client | Icon rows in JEED tooltips (JEED-only) |

`SimpleDiseasesMixinPlugin` skips JEED mixins when JEED is not loaded.

### HUD effect sort order

All Simple Diseases `MobEffect`s extend `SortedMobEffect` and use reserved low Forge `getSortOrder` bands from `EffectHudSort` so icons sort **left of vanilla/mod defaults** (immunity → disease paths → symptoms → injury/indicators). Tier variants of the same disease share one sort slot. `DiseaseMobEffect` inherits this via `SortedMobEffect`.

---

## Disease Registry

All diseases registered in `DiseaseRegistry.bootstrap()`, partitioned into `viral()`, `bacterial()`, `complications()`, `contagious()`, `environmental()`.

### Exclusion Groups

| Group | Members | Rule |
|---|---|---|
| `"viral"` | Cold, Flu, RSV, Norovirus | One active per player |
| `"bacterial"` | Cellulitis | One active wound infection |

Mutual exclusion enforced at `NULLIFY_THRESHOLD` (0.05) in `ContagionManager`.

---

## Diseases (Summary)

Full per-disease parameters live in `DiseaseRegistry.bootstrap()`. Quick reference:

| ID | Type | Tiers | Key notes |
|---|---|---|---|
| `cold` | Viral | 3 | Damp/windchill fallback; 16.7% P↔P spread |
| `flu` | Viral | 4 | Outbreak-only acquisition; fever all tiers |
| `rsv` | Viral | 3 | Winter-boosted damp/wind; suppressed during flu window |
| `norovirus` | Viral | 3 | Waterborne reservoirs; max-saturation debuff; puddles |
| `pneumonia` | Complication | 4 | Flu/Cold/RSV source; bad cough + breathless |
| `bronchitis` | Complication | 3 | Flu/Cold/RSV source |
| `cellulitis_staph` | Bacterial | 3 | Wound-seeded; `PainProfile.CELLULITIS` persistent pain |
| `sepsis_staph` | Complication | 4 | Cellulitis trigger; `PainProfile.SEPSIS`; no passive recovery |
| `mof_staph` | Complication | 1 | Lethal Wither-rate damage; status effect `mof` |

---

## Disease Symptom Pools

Authoritative source: `DiseaseRegistry.bootstrap()`. All diseases use pool thresholds **0.10 / 0.40 / 0.70** (up to 3 episodic slots) unless noted. **Persistent** effects are outside the pool (always on while latched via `PersistentEffectService`).

**Legend:** *static* = `SymptomTiming.STATIC` (HUD marker while in pool); *inherit-only* = `SymptomEntry.inheritOnly` (pool entry only via complication inheritance, never random); *inherit-capable* = listed in complication `commonAdds` for source matching; **ADV** = `SymptomBand.ADVANCED` (Severe+ to draw).

### `cold` — Viral · 3 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | — |
| Common | Coughing; Runny Nose; Headache (`NAUSEA`, 200 ticks); Sore Throat |
| Severe (ADV) | — |
| Persistent | Malaise |
| Episode pacing | 120–300 s interval · 30–60 s duration |

### `flu` — Viral · 4 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | — |
| Common | Coughing; Runny Nose; Headache (`NAUSEA`, 200 ticks); Sore Throat |
| Severe (ADV) | Vomiting (`DRAIN_FOOD`); Shortness of Breath (`BREATHLESS`, 200 ticks); Tachypnea; Tachycardia |
| Persistent | Malaise + Mild Pain (`PainProfile.MILD_FLAT`) |
| Episode pacing | 60–180 s · 45–90 s |

### `rsv` — Viral · 3 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | Wheezing |
| Common | Coughing; Runny Nose |
| Severe (ADV) | Shortness of Breath (`BREATHLESS`, 200 ticks); Tachypnea |
| Persistent | Malaise |
| Episode pacing | 90–210 s · 40–80 s |

### `norovirus` — Viral · 3 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | — |
| Common | Headache (`NAUSEA`, 200 ticks); Vomiting (`DRAIN_FOOD`); Diarrhea (`DRAIN_FOOD`); Stomach Cramps |
| Severe (ADV) | — |
| Persistent | Malaise |
| Episode pacing | 45–120 s · 30–60 s |

### `pneumonia` — Viral complication · 4 tiers · source: cold / flu / rsv

| Layer | Symptoms |
|---|---|
| Hallmarks | Shortness of Breath (`BREATHLESS`, 200 ticks); Bloody Coughing (`DAMAGE`, 100 ticks) |
| Common (*inherit-capable*) | Coughing; Runny Nose; Headache (`NAUSEA`, 200 ticks); Sore Throat; Vomiting (`DRAIN_FOOD`); Productive Coughing |
| Severe (ADV) | Tachypnea; Tachycardia; Confusion |
| Persistent | Malaise + Pain (`PainProfile.PNEUMONIA`) |
| Episode pacing | 30–90 s · 30–60 s |

### `bronchitis` — Viral complication · 3 tiers · source: cold / flu / rsv

| Layer | Symptoms |
|---|---|
| Hallmarks | Shortness of Breath (`BREATHLESS`, 200 ticks) |
| Common (*inherit-capable*) | Coughing; Runny Nose; Wheezing (*inherit-only*); Headache (`NAUSEA`, 200 ticks); Sore Throat; Vomiting (`DRAIN_FOOD`); Productive Coughing |
| Severe (ADV) | Tachypnea |
| Persistent | Malaise + Mild Pain (`PainProfile.MILD_FLAT`) |
| Episode pacing | 30–90 s · 30–60 s |

### `cellulitis_staph` — Bacterial · 3 tiers · wound-seeded

| Layer | Symptoms |
|---|---|
| Hallmarks | Localized Redness (*static*) |
| Common | — |
| Severe (ADV) | Hypotension (`HYPOTENSION`); Tachycardia; Confusion |
| Persistent | Malaise + Pain (`PainProfile.CELLULITIS`) |
| Episode pacing | 60–180 s · 30–90 s |
| Worsening thresholds | **1.5, 2.0** (aligned with cold; cap = 2.0) |

### `sepsis_staph` — Bacterial complication · 4 tiers · triggered by severe cellulitis at cap

| Layer | Symptoms |
|---|---|
| Hallmarks | Hypotension (`HYPOTENSION`) |
| Common | Localized Redness (*static*, *inherit-only*); Confusion; Tachycardia; Tachypnea |
| Severe (ADV) | Shortness of Breath (`BREATHLESS`, 200 ticks, *inherit-only*); Mottled Skin (*static*) |
| Exclusive pairs | Localized Redness ↔ Mottled Skin (bidirectional; adding one clears the other from pool) |
| Persistent | Malaise + Pain (`PainProfile.SEPSIS`) |
| Episode pacing | 90–240 s · 45–120 s |

Pre-latch: inherits matching active symptoms from cellulitis (e.g. redness from cellulitis hallmark). Post-latch: no new inheritance.

### `mof_staph` — Bacterial complication · 1 tier · triggered by Debilitating sepsis

| Layer | Symptoms |
|---|---|
| Pool | **Empty** (`SymptomConfig.empty()`) |
| Direct effect | 1 magic damage / 40 ticks while latched (Wither-I rate) |

---

## Severity Scale

Five tiers; diseases use a **window** centered on MODERATE.

| Tier | Duration × | Interval × | Debuff × | Roll Weight |
|---|---|---|---|---|
| LIGHT | 0.40 | 1.70 | 0.40 | 35 |
| MILD | 0.65 | 1.30 | 0.70 | 30 |
| MODERATE | 1.00 | 1.00 | 1.00 | 20 |
| SEVERE | 1.50 | 0.65 | 1.40 | 11 |
| DEBILITATING | 2.20 | 0.40 | 1.90 | 4 |

Rolled once at first latch. Honey reduces tier: 35% base, ×0.5 per prior success. Immunodeficiency rolls twice, keeps worst.

---

## Disease Tier Effects

Per-tier disease `MobEffect` variants carry **fever/shock metadata only** — no attack speed, mining, saturation, or other per-tier debuffs. Gameplay debuffs come from **symptoms** (episodic markers), **persistent Pain/Malaise** (`PersistentEffectService`), and the unified `pain` effect.

Each tier variant may register a hidden `MAX_HEALTH` `MULTIPLY_TOTAL` penalty (not shown in JEED):

| Condition | Penalty |
|---|---|
| Very High Fever (`FEVER_HIGH`, 40°C) | −5% max HP |
| Hyperpyrexia (`FEVER_SEVERE`, ≥41°C) | −15% max HP |
| Septic shock | −25% max HP |

Shock beats fever when both would apply. `DiseaseEvents.enforceMaxHealthCap` clamps current HP server-side when a penalty is active.

---

## Fever System

`double feverOffset` on `DiseaseMobEffect` — **not** an attribute modifier (recovery/warmth only). Max-health penalties are separate hidden modifiers (see **Disease Tier Effects**).

| Level | Offset (MC WORLD scale) | Tooltip / JEED display |
|---|---|---|
| Light | +0.05 | Mild Fever |
| Mild | +0.10 | High Fever |
| High | +0.15 | Very High Fever (−5% ♥) |
| Severe | +0.20 | Hyperpyrexia (−15% ♥) |

- **JEED disease tooltips:** `EffectRendererMixin` post-processes `EffectRenderer.getTooltipsWithDescription` (`require = 0`, JEED-only). `DiseaseTooltipHelper` strips the vanilla/JEED `"When Applied:"` attribute block (including hidden max-health lines), inserts a yellow **condition row** (`ConditionType` organ icon + label under the effect name), then a **Disease Symptoms:** section listing fever/shock, tier-scaled persistent pain, and active pool symptoms. Fever/shock/pain rows use `IconTextTooltipRenderer` icons.
- **Cold Sweat WORLD modifiers:** `FeverWorldTempModifier` (+) and `SepticShockTempModifier` (−) synced each tick via `ColdSweatCompat.syncDiseaseWorldModifiers`.
- **Malaise scaling:** `PersistentEffectService` applies infinite malaise while latched; amp = max `DiseaseMobEffect.malaiseAmplifierFrom()` across latched disease variants (fever/shock → amp 0–3).

---

## Recovery Multiplier (Centralized)

Passive recovery drains progress at `recoveryRate × multiplier`. Multiplier is **0.0–1.0** (minimum drain floor **0.25×** when partially warm). Requires environmental warmth, not elevated BODY temperature.

**API:** `ColdSweatCompat.getRecoveryMultiplier(player, exclusionGroup, envAccumulating)`

| Input | Effect |
|---|---|
| `envAccumulating == true` | Returns **0.0** immediately (viral only — damp/wind added progress this tick) |
| Warmth ≥ threshold | **1.0** |
| Warmth ≤ base floor | **0.25** (`SUPPRESSED_RECOVERY_MIN`) |
| Between floor and threshold | Linear **0.25 → 1.0** |

**Threshold:** `base_floor + max_fever_offset` vs `getObjectiveRecoveryWarmth()` (WORLD minus disease perception modifiers + insulation + hot waterskin). Fever raises the threshold; its WORLD modifier is stripped from warmth so body heat does not cheat the gate.

| Group | Base floor | Fever scale | When multiplier applies |
|---|---|---|---|
| Viral + viral complications | `MIN_WORLD_TEMP_TO_RECOVER` (**0.75**) | Full | Whole latched recovery; **paused** while damp/wind accumulates |
| Bacterial (cellulitis cap-recovery) | `MIN_WORLD_TEMP_TO_RECOVER_BACTERIAL` (**0.60**) | Full | Cap-recovery phase only; damp/wind do **not** pause drain |
| Sepsis / MOF | — | — | No passive recovery |

`isWarmEnoughForRecovery()` ≡ `getRecoveryMultiplier(..., false) >= 1.0`.

**`DiseaseContext`** (built once per tick in `DiseaseEvents`): precomputed `viralRecoveryMultiplier`, `bacterialRecoveryMultiplier`, `viralEnvironmentalAccumulating`, `complicationWorseningGroup`, `suppressedEpisodeSources`. Categories call `ctx.recoveryMultiplier(group)` — never re-query `ColdSweatCompat` inside category ticks.

---

## Stochastic Worsening (`WorseningRoll`)

Shared momentum model for viral, bacterial (cap phase), and stochastic viral complications:

```
chance(worsenings) = min(1.0, 0.30 + 0.25 × worsenings)
```

Replaces the old decay formula (`0.35 × 0.5^worsenings`). Used by `ViralCategory`, `BacterialCategory` (pre-cap), and `ComplicationCategory` (post-latch stochastic branch).

---

## Accumulation Fatigue (`AccumFatigueManager`)

Anti-exploit for standing in rain/wind while latched on a curable disease. **Not** a flat latched timer — streak grows only when **latched curable + viral env accumulating** this tick.

| Constant | Value |
|---|---|
| `ACCUM_FATIGUE_WARN_TICKS` | 8 min (9600 ticks) — actionbar warn |
| `ACCUM_FATIGUE_PENALTY_TICKS` | 15 min (18000 ticks) — apply `IMMUNE_DEFICIENCY` |
| `ACCUM_FATIGUE_DECAY_PER_TICK` | 1 — streak decays while latched but dry (prevents shelter-dip exploit) |

- **Latched curable:** any `inRecovery` disease except sepsis / MOF. Cellulitis counts but does not drive streak alone (needs viral damp/wind accum flag).
- **On cure:** clears fatigue-applied `IMMUNE_DEFICIENCY` and streak; does **not** remove `/sdimmune deficient`.
- Lang: `message.simplediseases.accum_fatigue_warn`, `message.simplediseases.accum_fatigue`.

---

## Symptom System

Layered config: `SymptomConfig` holds **hallmarks** (fixed-order pool fill), **commonAdds**, **severe** (`SymptomBand.ADVANCED`), plus **persistentEffects** (malaise + optional `PainProfile` via `PersistentEffectService`). Episodic rotation via `SymptomService` + `SymptomPoolComponent` (3 threshold slots: 0.10 / 0.40 / 0.70).

### SymptomEntry fields

| Field | Effect |
|---|---|
| `band` | `COMMON` (any tier) or `ADVANCED` (Severe+ to draw; sticky once in pool) |
| `timing` | `EPISODIC` (random episodes) or `STATIC` (infinite marker — Localized Redness, Mottled Skin) |
| `durationTicks` | Shorter impact window (NAUSEA, BREATHLESS, Bloody Coughing DAMAGE) |
| `amplifier` | Fixed episode amplifier when needed |
| `inheritOnly` | If true, enters pool only via complication inheritance — excluded from random draws and tier-worsen upgrades |

**Cough-variant exclusion:** Cough, Bloody Coughing, and Productive Coughing are mutually exclusive per disease pool.

**Exclusive pairs:** `SymptomSupersedes` entries in `SymptomConfig.exclusivePairs` — bidirectional pool exclusion (e.g. sepsis Localized Redness ↔ Mottled Skin). Cleared when the paired symptom is added to the pool.

**Complication inheritance:** Pre-latch `syncPool` inherits matching active source symptoms by `MobEffect` identity when the destination lists the same effect. Fill order per slot: destination hallmarks → inheritance → random common/severe. `inheritOnly` symptoms are eligible for inheritance but never drawn incidentally.

### Symptom Actions

| Action | On episode fire |
|---|---|
| `NONE` | Apply marker effect only (Confusion = HUD only, no NAUSEA yet) |
| `DAMAGE` | Bloody Coughing: magic 1♥/s for `durationTicks` via `BloodyCoughingEffect` NBT window |
| `DRAIN_FOOD` | −3 food, saturation → 0 (vomiting/diarrhea) |
| `NAUSEA` | Vanilla Confusion for duration (Headache) |
| `BREATHLESS` | Slowness IV (~60% slow) for `durationTicks` (200 ticks = 10 s for Shortness of Breath) |
| `HYPOTENSION` | No sound on fire; `HypotensionEffect` pulses hidden Blindness + Slowness IV every **30 s** for **10 s** while the episode marker is active |

**Treatment (interim):** Broth/honey suppress episodic firing only (`SYMPTOMS_MANAGED` / `TREATMENT_APPLIED`); pool bits, static markers, and persistent effects remain. Clearing a hypotension episode removes active blindness/slowness side effects.

### Symptom Gameplay (while episode marker active)

Effects keyed on symptom `MobEffect` presence — not tied to a specific disease.

| Symptom | Server | Client |
|---|---|---|
| **Shortness of Breath** | Hidden Slowness IV for 10 s on episode fire (`BREATHLESS`) | — |
| **Hypotension** | `HypotensionEffect.applyEffectTick`: Blindness + Slowness IV every 30 s (10 s each); silent | — |
| **Tachycardia** | `PlayerMixin`: doubles food exhaustion while sprinting | `GuiMixin.renderHeart`: per-heart Y jitter (low-health shake) |
| **Tachypnea** | `SymptomEvents.onLivingBreathe`: land sprint consumes 6/tick (Forge hook, no auto-refill); +4/tick refill when not sprinting; underwater +1 consume; vanilla drown at air ≤ −20 | Vanilla `AIR_LEVEL` bubbles; `TachypneaAirOverlay` at full air on land |
| **Stomach Cramps** | Blocks natural hunger regen (`SymptomEvents.onHeal`) | Red hunger-bar tint (`ForgeGuiMixin.renderFood`) |
| **Bloody Coughing** | `DAMAGE`: 1♥ magic/s for 100 ticks (`BloodyCoughingEffect` NBT window) | 4 staggered blood splats per episode (`bloody_cough` particle, `VomitParticle` physics) |
| **Productive Coughing** | Marker + sound only | 4 staggered sputum splats per episode (`sputum` particle) |

Cough bursts: offsets `{0, 8, 17, 26}` ticks, 1–2 particles per burst, chest height — scheduled in `DiseaseEvents.tickCoughParticles` (same episode-onset detection as vomit).

### Symptom-Driven Interactions (`SymptomEvents`)

| Trigger | Behavior |
|---|---|
| Sore Throat + eat | 0.5 HP magic damage per hunger point restored (on finish); actionbar message |
| Stomach Cramps + heal ≤ 1 HP (no Regen) | Cancel heal; red hunger-bar tint while active |
| Pain amp ≥ 3 + sleep | Block sleep (Severe / Excruciating Pain) |
| Malaise on player | Scale jump via `disease_jump_factor` on `LivingJumpEvent` |

---

## Injury & Wound System (`InjuryManager`)

Persisted in `PlayerInjuryState` (NBT under `"injury"` on player). Ticked from `DiseaseEvents.onPlayerTick`.

### Flesh wounds

**Lacerating** sources (≥ **4 HP** dealt): player/mob sharp weapons, arrows/tridents, mob bites. Tiered roll by armor (10/7/4/2% unarmored→heavy); high-damage bypass lowers effective armor tier; heavy armor + axe/crossbow bonus rolls.

On success: wound duration 3000/6000/9000 ticks (mild/moderate/severe). **Acute Pain** (amplifier 1) via `PersistentEffectService` while the wound is open (until cellulitis latches). Wounds deal no damage over time — their pressure is pain, blood visuals, and Cellulitis seeding. Flesh-wound severity drives both blood visuals directly (world blood trail + HUD splatter, see Visual Effects).

### Cellulitis seeding

Per-second chance while flesh wound open (pre-latch cellulitis), scaled by wound severity and immunity state.

---

## Visual Effects

### Shared disease status icons

Per-tier disease `MobEffect`s still register separately (fever/shock tier swapping, hidden max-HP penalties), but **all tiers of the same disease path share one HUD/inventory icon**:

- **Assets:** one PNG per disease path under `textures/mob_effect/` (e.g. `flu.png`, `pneumonia_flu.png`, `mof.png`) — 13 disease files instead of 42 tier-specific files. Symptom/indicator effects remain one PNG per registry name (`pain.png`, `cough.png`, etc.).
- **Registration:** `DiseaseEffects.registerVariants()` sets `DiseaseMobEffect.sharedIconId` to `simplediseases:<path>`.
- **Client:** `MobEffectTextureManagerMixin` redirects atlas sprite lookup to the shared id. MOF registers as a single effect `mof` (not `mof_staph_moderate`).

### Bleeding particles (Majrusz parity)

- **Textures:** `blood_0`–`blood_6` (MIT-licensed from Majrusz Progressive Difficulty)
- **Client:** `BleedingParticle` — ground-flattening splats, `quadSize 0.1 × 1.5` render scale, ~40 s lifetime
- **Server:** `DiseaseParticleEmitter.emitBleeding` every 3 ticks while a flesh wound is open; count `round(0.5 + 0.5 × (15 + severity) × walkDelta)` (severity 0–2); spawn at entity center; 0.25× spread
- **HUD:** `BleedingHudOverlay` — 6×4 pooled screen splatters; `BleedingSplatterPacket` via `NetworkHandler`. Burst of 4 on wound application, then periodic splatters at 600/300/120-tick intervals (2/3/3 splatters) by wound severity (`InjuryManager.tick`)

### Vomit particles

- **Textures:** separate `vomit_0`–`vomit_6` (green-tinted splats)
- **Client:** `VomitParticle` — same physics as bleeding, larger scale (`quadSize 1.5`), pre-green PNGs with brightness fade
- **Server:** ~2 s continuous burst when vomiting episode starts (`DiseaseEvents.tickVomitParticles`); spawn at chest height (`Y + 0.35 × height`); 2–4 particles per pulse

### Cough splatter particles (bloody / productive)

- **Types:** `bloody_cough` (reuses `blood_0`–`blood_6` textures) and `sputum` (`sputum_0`–`sputum_6`, light-green splats)
- **Client:** both use `VomitParticle.Provider` (mouth-eject → ground splat)
- **Server:** `DiseaseParticleEmitter.emitCoughSplatter` — 1–2 particles per burst; `DiseaseEvents.tickCoughParticles` fires 4 staggered bursts per episode

### Tachypnea air HUD

- **Client:** vanilla `AIR_LEVEL` overlay when air is below max; `TachypneaAirOverlay` draws the same `icons.png` bubble row at full air on land (resource-pack compatible)

Disease ambient particles (cold/flu/rsv/norovirus) still use `DiseaseParticleEmitter.tick` on latched viral recovery.

### Multiplayer visibility

| Effect | Other players nearby? | Mechanism |
|---|---|---|
| Body shiver (high fever / septic shock) | **Yes** | Synced `MobEffectInstance` + client `LivingEntityRendererMixin` (`FEVER_HIGH`+ or shock only) |
| Ground particles (blood, vomit, cough, sputum, disease ambient) | **Yes** | Server `sendParticles` (~32 block range); all clients need the mod |
| Symptom sounds | **Yes** | `level.playSound` at entity position |
| Screen blood HUD splatters, debug overlay HUD | **No** | `BleedingSplatterPacket` / `DebugOverlayPacket` → affected player only |
| Heart shake, food-bar tint, tachypnea air overlay | **No** | Local client HUD / mixins |

---

## Contagion (`ContagionManager`)

One `Channel` per contagious disease. Committed incubation model: `pendingIncubation` + `pendingIncubationId` on `PlayerDiseaseState`. Norovirus puddles via `LingeringNorovirusManager` (vomit/diarrhea during recovery, infected villagers).

---

## Acquisition Routes

| Route | Manager | Notes |
|---|---|---|
| Damp / wetness | `WetnessManager` | Rain, water, drying via CS WORLD trait; drenched tiers at 10/40/72% |
| Windchill | `WindchillManager` | Dry + cold; `Chilling Wind` indicator |
| Waterborne | `WaterborneManager` | Norovirus only; deterministic 32×32 reservoirs |
| Contact | `ContagionManager` | Proximity + incubation budget |
| Wounds | `InjuryManager` | Cellulitis seed from open flesh wounds |

**Damp/windchill picker:** RSV (winter boost) → Flu (outbreak only) → Cold default.

---

## Treatment & Cure

| Item / action | Effect |
|---|---|
| Warm broths | −0.1 all diseases; clear symptom episodes; `Symptoms Managed` 5 min |
| Honey | −0.5 progress; tier reduction roll; clears Symptoms Managed; `Treatment Applied` 5 min |
| Full sleep | −1.0 on viral + complications (no tier reduction) |
| Natural recovery | Viral/bacterial cap-recovery drain when warmth gate passes |

---

## Flu Season (`FluSeasonManager`)

Persisted `FluSeasonData`. Autumn 40% / Winter 60% season pick; 60% outbreak roll on window open. `/sdfluseason` force-toggle. RSV suppressed during any open flu window.

---

## Mod Compatibility

Known optional integrations follow the **cross-mod compatibility** rules above: `compat/` facades, runtime detection, offline fallbacks, and non-invasive hooks only.

| Mod | Integration |
|---|---|
| **Cold Sweat** | `ColdSweatCompat` — drying, damp/wind gates, recovery multiplier, objective recovery warmth, fever/shock WORLD modifiers, waterskin/goat fur; full fallback when absent |
| **Serene Seasons** | `SereneSeasonsCompat` — winter RSV/noro/flu; compile stubs excluded from JAR |
| **JEED** | Optional disease tooltip mixin + icon rows (`require = 0`; skipped entirely when JEED absent via `SimpleDiseasesMixinPlugin`) |

Never call Cold Sweat or Serene Seasons classes outside `*Compat` packages. All optional integrations must degrade gracefully on dedicated servers and in single-player without those mods installed. For mods not listed here, assume they are present in large modpacks — do not add integrations that require their APIs unless isolated in `compat/` with fallbacks.

---

## Custom Attributes (`DiseaseAttributes`)

All on `EntityType.PLAYER`, syncable `RangedAttribute`:

| Name | Default | Used by |
|---|---|---|
| `disease_max_saturation` | 5.0 | Reserved; `SymptomEvents` caps saturation when attribute &lt; 5.0 (no disease applies it currently) |
| `disease_knockback_factor` | 1.0 | Pain (`MULTIPLY_TOTAL` −10% per amp level) |
| `disease_block_break_speed` | 1.0 | Pain (`SymptomEvents.onBreakSpeed`) |
| `disease_jump_factor` | 1.0 | Malaise (`MULTIPLY_TOTAL`; scaled by amp + 1) |

---

## Pain Profiles & Tiers

Persistent pain uses a single `DiseaseEffects.PAIN` (`pain`) effect. Amplifier is chosen by `PainProfile` in each disease's `PersistentEffects` (via `PersistentEffectService`) and open flesh wounds. `PersistentEffectService` keeps the **highest** amplifier across all sources.

`PainProfile` mappings (amplifier → display name):

| Profile | Diseases | Mild / Light | Moderate | Severe | Debilitating |
|---|---|---|---|---|---|
| `MILD_FLAT` | Flu, Bronchitis | 0 Mild (I) | 0 | 0 | 0 |
| `PNEUMONIA` | Pneumonia | 1 Acute (II) | 1 | 2 Intense (III) | 2 |
| `CELLULITIS` | Cellulitis | 1 | 1 | 2 | 2 |
| `SEPSIS` | Sepsis | 2 Intense (III) | 2 | 3 Severe (IV) | 3 |

Open flesh wound (pre-cellulitis): amplifier **1** (Acute II).

`pain` applies −10% `MULTIPLY_TOTAL` per level to attack speed, attack damage, mining speed (`disease_block_break_speed`), knockback, and movement speed (effective scale = amp + 1).

Display labels: `simplediseases.pain.0` … `simplediseases.pain.4` (Mild I through Excruciating V). Sleep blocked at amp **≥ 3** (`message.simplediseases.pain_no_sleep`).

Configure in `DiseaseRegistry.bootstrap()` via `PersistentEffects.withPain(PainProfile.…)`.

---

## NBT Persistence (`PlayerDiseaseState`)

| Key | Content |
|---|---|
| `wet` | Wetness progress |
| `diseases` | Disease instance list |
| `groupImmunity` | Group → expiry tick |
| `pendingIncubation` / `pendingIncubationId` | Committed incubation |
| `wasInInfectedWater` | Waterborne edge-detect |
| `accumFatigueStreak` | Continuous damp/wind re-exposure streak (ticks) |
| `accumFatigueWarned` | 8-min warn already shown |
| `fatigueDeficiency` | Fatigue-applied immunodeficiency flag (distinct from `/sdimmune`) |
| `injury` | Flesh wound ticks, pain episode timer |

Contagion villager exposure is in-memory only.

---

## Debug Commands

Toggle debug overlays with `/sdviral` and `/sdbacterial` (run again to disable). While active, the server builds debug lines each tick in `DiseaseEvents` and syncs them to a **client HUD overlay** (`DebugOverlayPacket` → `ClientDebugOverlay`, drawn after the crosshair). Clearing a toggle sends an empty packet for that section.

| Command | Permission | Description |
|---|---|---|
| `/sdviral` | Any | Toggle viral debug HUD: diseases, wet/dry/W, `recov`, `ACCUM`, `exposure` (streak s), `drain` |
| `/sdbacterial` | Any | Toggle bacterial debug HUD: diseases, wound, `recov`, `drain` (cap-recovery only) |
| `/sdfluseason` | Any | Force flu season ON/OFF |
| `/sdimmune boost\|deficient\|clear` | Op | Immunity effects |
| `/sdaccumulate <disease> <amount>` | Op | Add progress; auto-seeds complication sources |
| `/sdinjury flesh_wound <0-2>` | Op | Add flesh wound severity |
| `/sdreservoir` | Op | Teleport to nearest noro reservoir |
| `/sdcheck` | Any | Villager disease status within 16 blocks |

---

## Key Conventions

- Register via `DeferredRegister` only (`DiseaseEffects.EFFECTS`, etc.) — never direct Forge registries.
- New diseases → `DiseaseRegistry.bootstrap()` with appropriate def type.
- Tier variants → `DiseaseEffects.registerVariants()`; fever/shock via `.fever()` / `.shock()` only on tier effects — **no per-tier attack/mining/saturation debuffs**. Hidden max-health penalties applied automatically for high fever / shock. Set `.sharedIcon()` to the disease path for shared HUD icons.
- Persistent pain → `PersistentEffects.withPain(PainProfile.…)` in `SymptomConfig`; applied by `PersistentEffectService`, not manual effect applies.
- JEED tooltips → `DiseaseTooltipHelper` + `ConditionType`; do not add fever as a visible attribute modifier.
- MOF → single registered effect `DiseaseEffects.MOF` (`mof`), not a tier variant map.
- Complications → `triggeredBy` source required; progress gated on active source.
- Symptom episodes → `SymptomService` only; add to `SymptomConfig`, not manual tick applies.
- Compat → `ColdSweatCompat` / `SereneSeasonsCompat` exclusively; always provide fallbacks when mods are absent.
- Recovery → precompute multipliers in `DiseaseEvents`; categories consume `DiseaseContext.recoveryMultiplier(group)`.
- Worsening rolls → `WorseningRoll.chance(worsenings)` for momentum stochastic tiers.
- Accum fatigue → `AccumFatigueManager.tick()` after env-accum flag is known; never duplicate damp/wind detection.
- Bleeding/vomit/cough visuals → server `sendParticles` (multiplayer-visible) + client particle classes; HUD splatter via player-only network packet.
- Performance / multiplayer / cross-mod compat / code optimization → see **Development Principles**; do not regress them for convenience. Prefer Forge APIs over mixins; keep hooks non-invasive for 100+ mod modpacks.
- Symptom side effects with continuous behavior → custom `MobEffect` subclass (`HypotensionEffect`, `BloodyCoughingEffect`) or mixins (`PlayerMixin`, `GuiMixin`).
