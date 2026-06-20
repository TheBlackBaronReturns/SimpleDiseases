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

## Architecture Overview

ECS-lite disease model: `PlayerDiseaseState` holds per-disease `DiseaseInstance` component bags, plus wetness, incubation, group immunity, and `PlayerInjuryState`. Categories (`ViralCategory`, `BacterialCategory`, `ComplicationCategory`) tick via `DiseaseContext` built each tick in `DiseaseEvents`.

```
com.theblackbaron.simplediseases
├── SimpleDiseases.java           — @Mod root; registers + event buses
├── client/                       — Particles, HUD overlay, client tick
├── command/SdCommands.java       — /sd* debug and admin commands
├── compat/                       — Cold Sweat + Serene Seasons (never call mods elsewhere)
├── event/                        — DiseaseEvents, CureEvents, SymptomEvents
├── mixin/                        — JEED tooltips, HUD shake/tint, tachypnea air, sprint hunger, shared icons
├── network/                      — BleedingSplatterPacket + NetworkHandler
├── particle/                     — DiseaseParticles registry + DiseaseParticleEmitter
├── sound/DiseaseSounds.java
└── status/                       — Effects, attributes, defs, managers, services
```

**Wiring (`SimpleDiseases` constructor):** `DiseaseAttributes` → `DiseaseEffects` → `DiseaseParticles` / `DiseaseSounds` → `DiseaseRegistry.bootstrap()` → Forge event handlers.

### Mixins (`simplediseases.mixins.json`)

| Mixin | Side | Role |
|---|---|---|
| `PlayerMixin` | common | Double sprint food exhaustion during tachycardia |
| `GuiMixin` | client | Tachycardia low-health heart shake |
| `ForgeGuiMixin` | client | Stomach cramps red food-bar tint |
| `LivingEntityRendererMixin` | client | Fever/septic body shiver |
| `MobEffectTextureManagerMixin` | client | Shared per-disease HUD icons |
| `EffectRendererMixin` | client | JEED tooltip rows (`require = 0`) |
| `ScreenExtensionsHandlerMixin` | client | Icon rows in JEED tooltips |

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
| `cellulitis_staph` | Bacterial | 3 | Wound-seeded; Pain II persistent |
| `sepsis_staph` | Complication | 4 | Cellulitis trigger; Pain III; no passive recovery |
| `mof_staph` | Complication | 1 | Lethal Wither-rate damage; status effect `mof` |

---

## Disease Symptom Pools

Authoritative source: `DiseaseRegistry.bootstrap()`. All diseases use pool thresholds **0.10 / 0.40 / 0.70** (up to 3 episodic slots) unless noted. **Persistent** effects are outside the pool (always on while latched via `PersistentEffectService`).

**Legend:** *static* = `SymptomTiming.STATIC` (HUD marker while in pool); *inherit-only* = `SymptomEntry.inheritOnly` (pool entry only via complication inheritance, never random); *inherit-capable* = listed in complication `commonAdds` for source matching; **ADV** = `SymptomBand.ADVANCED` (Severe+ to draw).

### `cold` — Viral · 3 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | — |
| Common | Coughing; Runny Nose; Sore Throat |
| Severe (ADV) | — |
| Persistent | Malaise |
| Episode pacing | 120–300 s interval · 30–60 s duration |

### `flu` — Viral · 4 tiers

| Layer | Symptoms |
|---|---|
| Hallmarks | — |
| Common | Coughing; Runny Nose; Headache (`NAUSEA`, 200 ticks); Sore Throat |
| Severe (ADV) | Vomiting (`DRAIN_FOOD`); Shortness of Breath (`BREATHLESS`, 200 ticks); Tachypnea; Tachycardia |
| Persistent | Malaise |
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
| Persistent | Malaise + Pain II |
| Episode pacing | 30–90 s · 30–60 s |

### `bronchitis` — Viral complication · 3 tiers · source: cold / flu / rsv

| Layer | Symptoms |
|---|---|
| Hallmarks | Shortness of Breath (`BREATHLESS`, 200 ticks) |
| Common (*inherit-capable*) | Coughing; Runny Nose; Headache (`NAUSEA`, 200 ticks); Sore Throat; Vomiting (`DRAIN_FOOD`); Productive Coughing |
| Severe (ADV) | Tachypnea |
| Persistent | Malaise + Pain I |
| Episode pacing | 30–90 s · 30–60 s |

### `cellulitis_staph` — Bacterial · 3 tiers · wound-seeded

| Layer | Symptoms |
|---|---|
| Hallmarks | Localized Redness (*static*) |
| Common | — |
| Severe (ADV) | Hypotension (`HYPOTENSION`); Tachycardia; Confusion |
| Persistent | Malaise + Pain II |
| Episode pacing | 60–180 s · 30–90 s |

### `sepsis_staph` — Bacterial complication · 4 tiers · triggered by severe cellulitis at cap

| Layer | Symptoms |
|---|---|
| Hallmarks | Hypotension (`HYPOTENSION`) |
| Common | Localized Redness (*static*, *inherit-only*); Confusion; Tachycardia; Tachypnea |
| Severe (ADV) | Shortness of Breath (`BREATHLESS`, 200 ticks, *inherit-only*); Mottled Skin (*static*) |
| Exclusive pairs | Localized Redness ↔ Mottled Skin (bidirectional; adding one clears the other from pool) |
| Persistent | Malaise + Pain III |
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

## Fever System

`double feverOffset` on `DiseaseMobEffect` — **not** an attribute modifier.

| Level | Offset | Display |
|---|---|---|
| Light | +10 | Yellow |
| Mild | +20 | Gold |
| High | +35 | Red |
| Severe | +50 | Dark red |

- **Tooltip:** `EffectRendererMixin` injects colored fever/shock/pain/symptom rows under JEED `"potion.whenDrank"` (`require = 0`). Fever and septic shock use tier-specific icons (`fever_light` … `fever_severe`, `septic_shock`) via `IconTextTooltipRenderer`.
- **Cold Sweat WORLD modifiers:** `FeverWorldTempModifier` (+) and `SepticShockTempModifier` (−) synced each tick via `ColdSweatCompat.syncDiseaseWorldModifiers`.
- **Malaise scaling:** `PersistentEffectService` applies infinite malaise while latched; amp = max `DiseaseMobEffect.malaiseAmplifierFrom()` across latched disease variants (fever/shock → amp 0–3).

---

## Recovery Gate (Centralized)

Passive recovery requires environmental warmth, not elevated BODY temperature.

**API:** `ColdSweatCompat.isWarmEnoughForRecovery(player, exclusionGroup)`
- Viral / complications: full fever offset (`isWarmEnoughToRecover()` wrapper)
- Bacterial: fever offset × **0.5** (`isWarmEnoughForBacterialRecovery()` wrapper)
- Threshold: `MIN_WORLD_TEMP_TO_RECOVER` (0.75) + scaled fever offset vs `getObjectiveRecoveryWarmth()` (WORLD minus disease perception modifiers + insulation + hot waterskin)

**Per-tick suppression:** `DiseaseEvents` builds `suppressedRecoveryGroups` (`Set<String>`) on `DiseaseContext`:
- **Viral:** damp exposure, active windchill accumulation, or failed warmth check
- **Bacterial:** failed warmth check only (damp/wind do not block cellulitis recovery)

Categories call `ctx.suppressRecovery(group)` before draining progress (`ViralCategory`, `ComplicationCategory`, `BacterialCategory` cap-recovery).

---

## Symptom System

Layered config: `SymptomConfig` holds **hallmarks** (fixed-order pool fill), **commonAdds**, **severe** (`SymptomBand.ADVANCED`), plus **persistentEffects** (malaise + Pain via `PersistentEffectService`). Episodic rotation via `SymptomService` + `SymptomPoolComponent` (3 threshold slots: 0.10 / 0.40 / 0.70).

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
| Pain amp ≥ 2 + sleep | Block sleep |
| Active norovirus tier | Cap saturation at `disease_max_saturation` |
| Malaise on player | Scale jump via `disease_jump_factor` on `LivingJumpEvent` |

---

## Injury & Wound System (`InjuryManager`)

Persisted in `PlayerInjuryState` (NBT under `"injury"` on player). Ticked from `DiseaseEvents.onPlayerTick`.

### Bleeding (external)

| Source | Rule |
|---|---|
| Tiered weapon hit | ≥ 5 HP (≥ 4 HP sharp mob melee); armor-tier chance 10/8/5/1%; amount 2.5/1.5/1.0/0.5 |
| Mob bite | 50% +1.0 light bleed (Zombie, Spider, Animal; llama excluded) |
| Cactus | +1.0 every 20 ticks |
| Bare-hand glass break | +1.0 light bleed |
| Flesh wound applied | Bonus bleed 0.5 / 1.0 / 1.5 by wound severity |

`BleedingEffect` (amp > 0): periodic magic damage + HUD splatter packet. Decays over time; flesh wounds set a bleeding floor by severity.

### Flesh wounds

**Lacerating** sources (≥ **4 HP** dealt): player/mob sharp weapons, arrows/tridents, mob bites. Tiered roll by armor (10/7/4/2% unarmored→heavy); high-damage bypass lowers effective armor tier; heavy armor + axe/crossbow bonus rolls.

On success: wound duration 3000/6000/9000 ticks (mild/moderate/severe), bonus bleeding. **Pain I** is applied continuously via `PersistentEffectService` while the wound is open (until cellulitis latches).

### Internal bleeding

15% on blunt/fall/explosion hits ≥ 5 HP (not arrows/axes). Scales with damage and armor.

### Blood loss

Total wound load ≥ 3.5 and HP ≤ 6 → `blood_loss` effect (HP floor 6).

### Cellulitis seeding

Per-second chance while flesh wound open (pre-latch cellulitis), scaled by wound severity and immunity state.

---

## Visual Effects

### Shared disease status icons

Per-tier disease `MobEffect`s still register separately (gameplay modifiers, fever, tier swapping unchanged), but **all tiers of the same disease path share one HUD/inventory icon**:

- **Assets:** one PNG per disease path under `textures/mob_effect/` (e.g. `flu.png`, `pneumonia_flu.png`, `mof.png`) — 13 disease files instead of 42 tier-specific files. Symptom/indicator effects remain one PNG per registry name (`pain.png`, `cough.png`, etc.).
- **Registration:** `DiseaseEffects.registerVariants()` sets `DiseaseMobEffect.sharedIconId` to `simplediseases:<path>`.
- **Client:** `MobEffectTextureManagerMixin` redirects atlas sprite lookup to the shared id. MOF registers as a single effect `mof` (not `mof_staph_moderate`).

### Bleeding particles (Majrusz parity)

- **Textures:** `blood_0`–`blood_6` (MIT-licensed from Majrusz Progressive Difficulty)
- **Client:** `BleedingParticle` — ground-flattening splats, `quadSize 0.1 × 1.5` render scale, ~40 s lifetime
- **Server:** `DiseaseParticleEmitter.emitBleeding` every 3 ticks; count `round(0.5 + 0.5 × (15 + amp) × walkDelta)`; spawn at entity center; 0.25× spread
- **HUD:** `BleedingHudOverlay` — 6×4 pooled screen splatters on bleed damage pulse; `BleedingSplatterPacket` via `NetworkHandler`

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

| Mod | Integration |
|---|---|
| **Cold Sweat** | `ColdSweatCompat` — drying, damp/wind gates, objective recovery warmth, fever/shock WORLD modifiers, waterskin/goat fur |
| **Serene Seasons** | `SereneSeasonsCompat` — winter RSV/noro/flu; compile stubs excluded from JAR |
| **JEED** | Optional fever tooltip mixin |

Never call Cold Sweat or Serene Seasons classes outside `*Compat` packages.

---

## Custom Attributes (`DiseaseAttributes`)

All on `EntityType.PLAYER`, syncable `RangedAttribute`:

| Name | Default | Used by |
|---|---|---|
| `disease_max_saturation` | 5.0 | Norovirus (ADDITION) |
| `disease_knockback_factor` | 1.0 | Cellulitis, Pain |
| `disease_block_break_speed` | 1.0 | Respiratory diseases, Pain |
| `disease_jump_factor` | 1.0 | Malaise |

---

## Pain Tiers

`DiseaseEffects.PAIN` (`pain`) — `DiseaseMobEffect` with −10% `MULTIPLY_TOTAL` on attack speed, attack damage, mining speed, knockback, movement speed (scaled by amp + 1).

| Context | Amplifier | Display |
|---|---|---|
| Open flesh wound (persistent) | 0 | Pain I |
| Bronchitis (persistent) | 0 | Pain I |
| Pneumonia / Cellulitis (persistent) | 1 | Pain II |
| Sepsis (persistent) | 2 | Pain III |

Sleep blocked at amp ≥ 2 (`message.simplediseases.pain_no_sleep`).

---

## NBT Persistence (`PlayerDiseaseState`)

| Key | Content |
|---|---|
| `wet` | Wetness progress |
| `diseases` | Disease instance list |
| `groupImmunity` | Group → expiry tick |
| `pendingIncubation` / `pendingIncubationId` | Committed incubation |
| `wasInInfectedWater` | Waterborne edge-detect |
| `injury` | Bleeding, internal bleeding, flesh wound ticks, pain episode timer |

Contagion villager exposure is in-memory only.

---

## Debug Commands

| Command | Permission | Description |
|---|---|---|
| `/sddebugviral` | Any | Viral debug overlay toggle |
| `/sddebugbacterial` | Any | Bacterial debug overlay toggle |
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
- Tier variants → `DiseaseEffects.registerVariants()`; fever via `.fever()`, not attributes. Set `.sharedIcon()` to the disease path for shared HUD icons.
- MOF → single registered effect `DiseaseEffects.MOF` (`mof`), not a tier variant map.
- Complications → `triggeredBy` source required; progress gated on active source.
- Symptom episodes → `SymptomService` only; add to `SymptomConfig`, not manual tick applies.
- Compat → `ColdSweatCompat` / `SereneSeasonsCompat` exclusively.
- Recovery suppression → build in `DiseaseEvents`, consume via `DiseaseContext.suppressRecovery`.
- Bleeding/vomit/cough visuals → server `sendParticles` + client particle classes; HUD splatter via network packet.
- Symptom side effects with continuous behavior → custom `MobEffect` subclass (`HypotensionEffect`, `BloodyCoughingEffect`) or mixins (`PlayerMixin`, `GuiMixin`).
