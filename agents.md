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
├── mixin/                        — JEED fever tooltips, shivering, frostbite suppress
├── network/                      — BleedingSplatterPacket + NetworkHandler
├── particle/                     — DiseaseParticles registry + DiseaseParticleEmitter
├── sound/DiseaseSounds.java
└── status/                       — Effects, attributes, defs, managers, services
```

**Wiring (`SimpleDiseases` constructor):** `DiseaseAttributes` → `DiseaseEffects` → `DiseaseParticles` / `DiseaseSounds` → `DiseaseRegistry.bootstrap()` → Forge event handlers.

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
| `cellulitis_staph` | Bacterial | 3 | Wound-seeded; Sharp Pain II persistent |
| `sepsis_staph` | Complication | 4 | Cellulitis trigger; Sharp Pain III; no passive recovery |
| `mof_staph` | Complication | 1 | Lethal Wither-rate damage from septic shock |

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

- **Tooltip:** `EffectRendererMixin` injects colored fever label under JEED `"potion.whenDrank"` (`require = 0`).
- **Cold Sweat WORLD modifiers:** `FeverWorldTempModifier` (+) and `SepticShockTempModifier` (−) synced each tick via `ColdSweatCompat.syncDiseaseWorldModifiers`.
- **Malaise scaling:** `SymptomEntry.withFeverAmp()` sets malaise amplifier from active disease tier via `DiseaseMobEffect.malaiseAmplifierFrom()` (fever/shock → amp 0–3).

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

Episodic `MobEffectInstance`s via `SymptomService` + `SymptomPoolComponent`. Configured per disease in `SymptomConfig` / `SymptomEntry`.

### SymptomEntry flags

| Flag | Effect |
|---|---|
| `minSeverity` | Minimum rolled tier to enter pool |
| `severityAmp` | Episode amplifier scales with illness tier |
| `feverAmp` | Malaise amplifier from disease fever/shock tier |
| `amplifier` | Fixed episode amplifier (e.g. sepsis Sharp Pain = 2) |
| `durationTicks` | Shorter impact window (NAUSEA, BREATHLESS, DAMAGE) |

### Symptom Actions

| Action | On episode fire |
|---|---|
| `NONE` | Apply marker effect only |
| `DAMAGE` | 0.5 HP (`COUGH_FIT` for bad cough) |
| `DRAIN_FOOD` | −3 food, saturation → 0 (vomiting/diarrhea) |
| `NAUSEA` | Vanilla Confusion for duration |
| `BREATHLESS` | Slowness IV (~60% slow) for duration |

### Symptom-Driven Interactions (`SymptomEvents`)

| Trigger | Behavior |
|---|---|
| Sore Throat + eat | Cancel eat; actionbar message |
| Stomach Cramps + heal ≤ 1 HP (no Regen) | Cancel heal |
| Sharp Pain amp ≥ 2 + sleep | Block sleep |
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

On success: wound duration 3000/6000/9000 ticks (mild/moderate/severe), bonus bleeding, **immediate Sharp Pain I** (20–40 s), then episodic Sharp Pain I every 45–90 s until cellulitis latches or `Treatment Applied`.

### Internal bleeding

15% on blunt/fall/explosion hits ≥ 5 HP (not arrows/axes). Scales with damage and armor.

### Blood loss

Total wound load ≥ 3.5 and HP ≤ 6 → `blood_loss` effect (HP floor 6).

### Cellulitis seeding

Per-second chance while flesh wound open (pre-latch cellulitis), scaled by wound severity and immunity state.

---

## Visual Effects

### Bleeding particles (Majrusz parity)

- **Textures:** `blood_0`–`blood_6` (MIT-licensed from Majrusz Progressive Difficulty)
- **Client:** `BleedingParticle` — ground-flattening splats, `quadSize 0.1 × 1.5` render scale, ~40 s lifetime
- **Server:** `DiseaseParticleEmitter.emitBleeding` every 3 ticks; count `round(0.5 + 0.5 × (15 + amp) × walkDelta)`; spawn at entity center; 0.25× spread
- **HUD:** `BleedingHudOverlay` — 6×4 pooled screen splatters on bleed damage pulse; `BleedingSplatterPacket` via `NetworkHandler`

### Vomit particles

- **Textures:** separate `vomit_0`–`vomit_6` (green-tinted splats)
- **Client:** `VomitParticle` — same physics as bleeding, larger scale (`quadSize 1.5`), pre-green PNGs with brightness fade
- **Server:** ~4 s burst when vomiting episode starts (`DiseaseEvents.tickVomitParticles`); spawn at chest height (`Y + 0.35 × height`); 2–4 particles per pulse

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
| `disease_knockback_factor` | 1.0 | Cellulitis, Sharp Pain |
| `disease_block_break_speed` | 1.0 | Respiratory diseases, Sharp Pain |
| `disease_jump_factor` | 1.0 | Malaise |

---

## Sharp Pain Tiers

`DiseaseMobEffect` with −10% `MULTIPLY_TOTAL` on attack speed, attack damage, mining speed, knockback, movement speed (scaled by amp + 1).

| Context | Amplifier | Display |
|---|---|---|
| Open flesh wound (episodic) | 0 | Sharp Pain I |
| Cellulitis (persistent symptom) | 1 | Sharp Pain II |
| Sepsis (persistent symptom) | 2 | Sharp Pain III |

Sleep blocked at amp ≥ 2.

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
- Tier variants → `DiseaseEffects.registerVariants()`; fever via `.fever()`, not attributes.
- Complications → `triggeredBy` source required; progress gated on active source.
- Symptom episodes → `SymptomService` only; add to `SymptomConfig`, not manual tick applies.
- Compat → `ColdSweatCompat` / `SereneSeasonsCompat` exclusively.
- Recovery suppression → build in `DiseaseEvents`, consume via `DiseaseContext.suppressRecovery`.
- Bleeding/vomit visuals → server `sendParticles` + client particle classes; HUD splatter via network packet.
