# agents.md — SimpleDiseases Developer Reference

**Mod:** Simple Diseases | **ID:** `simplediseases` | **Version:** 0.1.0
**Stack:** Minecraft 1.20.1 · Forge 47.4.10 · Parchment 2023.09.03-1.20.1 · Java 17
**Author:** The Black Baron | **License:** MIT
**Repository:** https://github.com/TheBlackBaronReturns/SimpleDiseases

---

## Build Commands

```bash
./gradlew build        # Compile and package JAR
./gradlew runClient    # Launch Minecraft client with the mod loaded
./gradlew runServer    # Launch dedicated server
./gradlew runData      # Regenerate data assets
```

No test framework. Verify features by running the client.

---

## Package Structure

```
com.theblackbaron.simplediseases
├── SimpleDiseases.java              — @Mod root; wires all registers + event buses
├── client/                          — Client-only event handlers + custom particles
├── command/SdCommands.java          — All /sd* debug and admin commands
├── compat/
│   ├── ColdSweatCompat.java         — Cold Sweat integration (temp, drying, fever gate)
│   └── SereneSeasonsCompat.java     — Serene Seasons integration (season/winter queries)
├── event/
│   ├── DiseaseEvents.java           — Core per-tick disease logic (acquisition, progression, symptoms)
│   ├── CureEvents.java              — Treatment items + sleep recovery
│   └── SymptomEvents.java           — Symptom-driven interactions (sore throat, cramps, sleep block)
├── mixin/
│   └── EffectRendererMixin.java     — JEED tooltip injection (fever label in "When Applied:")
├── particle/                        — Custom particle types + emitter
├── sound/DiseaseSounds.java         — Custom sound events
└── status/
    ├── DiseaseAttributes.java       — Custom RangedAttribute registrations (saturation, knockback, mining)
    ├── DiseaseEffects.java          — All MobEffect registrations + per-tier variant builder
    ├── DiseaseMobEffect.java        — MobEffect subclass with ordered modifiers + fever offset
    ├── category/                    — Disease category singletons (VIRAL, COMPLICATION, BACTERIAL)
    ├── component/                   — ECS-lite component types (Progress, Immunity, Tier, Source, etc.)
    ├── def/                         — Disease definitions (ViralDiseaseDef, BacterialDiseaseDef, etc.)
    ├── manager/                     — Per-player state managers (ContagionManager, WetnessManager, etc.)
    └── service/                     — SymptomService, ImmunityService
```

---

## Disease Registry

All diseases are registered in `DiseaseRegistry.bootstrap()` and keyed by `ResourceLocation`. The registry is partitioned into lists: `viral()`, `bacterial()`, `complications()`, `contagious()`, `environmental()`.

### Exclusion Groups

- `"viral"` — Cold, Flu, RSV, Norovirus. Only one member can be active on a player at a time.
- `"bacterial"` — Cellulitis. Only one bacterial wound infection active at a time.

---

## Diseases

### 1. Cold (Rhinovirus) — `simplediseases:cold`

| Parameter | Value |
|---|---|
| Tiers | 3 (Mild / Moderate / Severe) |
| Color | `0xF0C1BA` (pale pink) |
| Particle | `cold` |
| Progress cap | 2.0 |
| Latch threshold | 1.0 |
| Recovery rate | 0.000030/tick |
| Immunity after cure | 20 min (24 000 ticks) |
| Incubation range | 0.1 – 0.5 |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Speed: −5% (MULTIPLY_TOTAL)
- Mining Speed: −5% (MULTIPLY_TOTAL)

**Fever:** Severe tier only → Light Fever (+10 CS BODY recovery threshold)

**Symptoms:** Coughing, Runny Nose, Malaise *(Cough and Sneeze sounds)*

**Acquisition:** Default fallback disease for the `viral` group during Damp or Windchill exposure. Never triggered during an active flu outbreak.

**Contagion:** Player↔Player and Villager↔Player within 6-block radius. Transmission chance 16.7%/tick. Villagers have 5% spawn-sick chance.

---

### 2. Influenza — `simplediseases:flu`

| Parameter | Value |
|---|---|
| Tiers | 4 (Mild / Moderate / Severe / Debilitating) |
| Color | `0xC8302E` (deep red) |
| Particle | `flu` |
| Progress cap | 10.0 |
| Latch threshold | 1.0 |
| Recovery rate | 0.000030/tick |
| Immunity after cure | 40 min (48 000 ticks) |
| Incubation range | 0.5 – 1.0 |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Speed: −10% (MULTIPLY_TOTAL)
- Mining Speed: −10% (MULTIPLY_TOTAL)

**Fever by tier:**
| Tier | Fever Level | CS BODY offset |
|---|---|---|
| Mild | Mild Fever | +20 |
| Moderate | High Fever | +35 |
| Severe | Severe Fever | +50 |
| Debilitating | Severe Fever | +50 |

**Symptoms (tier-gated):**
- All tiers: Coughing, Runny Nose, Malaise, Headache (→ Nausea), Sore Throat
- Severe+: Vomiting (→ drains food), Shortness of Breath (→ breathlessness)

**Acquisition:** Requires an active flu outbreak. 60% acquisition chance per exposure roll. Not acquirable via damp/windchill route without an outbreak.

**Contagion:** Player↔Player and Villager↔Player within 6-block radius. Transmission chance 33.4%/tick. Villager exposure threshold: 20. No spawn-sick villagers.

---

### 3. RSV — `simplediseases:rsv`

| Parameter | Value |
|---|---|
| Tiers | 3 (Mild / Moderate / Severe) |
| Color | `0xF2D027` (yellow) |
| Particle | `rsv` |
| Progress cap | 10.0 |
| Recovery rate | 0.000030/tick |
| Immunity after cure | 30 min (36 000 ticks) |
| Incubation range | 0.1 – 0.5 |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Speed: −5% (MULTIPLY_TOTAL)
- Mining Speed: −5% (MULTIPLY_TOTAL)

**Fever by tier:**
| Tier | Fever Level | CS BODY offset |
|---|---|---|
| Mild | None | — |
| Moderate | Light Fever | +10 |
| Severe | Mild Fever | +20 |

**Symptoms:** Coughing, Runny Nose, Malaise

**Acquisition:** Damp or Windchill. Base chance 20%; elevated to 40% during Serene Seasons winter. Excluded during active flu season window.

**Contagion:** Player↔Player and Villager↔Player. Transmission chance 33.4%/tick. Baby villagers have 15% spawn-sick chance.

---

### 4. Norovirus — `simplediseases:norovirus`

| Parameter | Value |
|---|---|
| Tiers | 3 (Mild / Moderate / Severe) |
| Color | `0x5B8C3E` (dark green) |
| Particle | `norovirus` |
| Progress cap | 2.0 |
| Recovery rate | 0.00006/tick (2× faster than respiratory) |
| Immunity after cure | 5 min (6 000 ticks) |
| Incubation range | 0.1 – 0.5 |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Max Saturation: −2.0 (ADDITION on `disease_max_saturation`, default 5.0)

**Fever:** Severe tier only → Light Fever (+10 CS BODY recovery threshold)

**Symptoms:** Malaise, Headache (→ Nausea), Vomiting (→ drains food), Diarrhea (→ drains food), Stomach Cramps

**Acquisition:** Waterborne only. Standing in deterministic "infected reservoir" water regions (32×32 blocks, ~5% of regions year-round; extra zones in Serene Seasons winter). Zones rotate every 7 in-game days via a world-seed hash — no stored state. Not acquirable via damp/windchill.

**Contagion:** Player↔Player proximity only. No villager proximity spread; infected villagers can leave lingering puddles (50% chance every 5 min). 5% per-villager seeding chance at Serene Seasons winter onset.

---

### 5. Pneumonia — `simplediseases:pneumonia`

Viral complication. Source can be Flu, Cold, or RSV.

| Parameter | Value |
|---|---|
| Tiers | 4 (Mild / Moderate / Severe / Debilitating) |
| Color | `0x6B5876` (muted purple) |
| Latch time | ~15–30 min from source activation |
| Recovery | Inherits source viral recovery rate |
| Worsening | Stochastic momentum (thresholds at 3.0, 6.0, 8.0) |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Speed: −20% (MULTIPLY_TOTAL)
- Mining Speed: −20% (MULTIPLY_TOTAL)

**Fever by tier:** Same distribution as Flu — Mild→Mild, Moderate→High, Severe→Severe, Debilitating→Severe.

**Symptoms (all tiers):** Bad Cough (→ damage, from LIGHT), Shortness of Breath (→ breathlessness, from LIGHT), Coughing, Runny Nose, Malaise, Headache (→ Nausea), Sore Throat, Vomiting (→ drains food)

---

### 6. Bronchitis — `simplediseases:bronchitis`

Viral complication. Source can be Flu, Cold, or RSV.

| Parameter | Value |
|---|---|
| Tiers | 3 (Mild / Moderate / Severe) |
| Color | `0x6B5876` |
| Latch time | ~15–30 min from source activation |
| Worsening | Stochastic momentum (thresholds at 4.0, 7.0) |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Speed: −15% (MULTIPLY_TOTAL)
- Mining Speed: −15% (MULTIPLY_TOTAL)

**Fever by tier:** Same as RSV — Mild→None, Moderate→Light, Severe→Mild.

**Symptoms:** Shortness of Breath (from LIGHT), Coughing (from LIGHT), Runny Nose, Malaise, Headache (→ Nausea), Sore Throat, Vomiting

---

### 7. Staph Cellulitis — `simplediseases:cellulitis_staph`

Bacterial wound infection. Not contagious.

| Parameter | Value |
|---|---|
| Tiers | 3 (Mild / Moderate / Severe) |
| Color | `0xCC4422` (brick red) |
| Progress cap | 2.0 |
| Accumulation rate | 1/4800 per tick |
| Natural recovery | 1/9000 per tick |
| Decay rate (pre-latch) | 1/12000 per tick |
| Worsening thresholds | 4/3, 5/3 (2 thresholds for 3 tiers) |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Attack Damage: −10% (MULTIPLY_TOTAL)
- Knockback Factor: −10% (MULTIPLY_TOTAL on `disease_knockback_factor`)

**Fever by tier:**
| Tier | Fever Level | CS BODY offset |
|---|---|---|
| Mild | Mild Fever | +20 |
| Moderate | High Fever | +35 |
| Severe | Severe Fever | +50 |

**Symptoms:** Sharp Pain (persistent), Malaise, Localized Redness

**Acquisition:** Seeded by flesh wounds from sharp damage (swords, axes, arrows, tridents, shears) when HP ≥ 4 and armor ≤ 7 toughness. Cumulative infection chance across wound lifetime by immunity state:
- Normal: ~20% / ~35% / ~45% per wound phase
- Immunodeficient: ~35% / ~45% / ~60%
- Immune boosted: ~5% / ~10% / ~17%

---

### 8. Staph Sepsis — `simplediseases:sepsis_staph`

Bacterial complication triggered when Cellulitis reaches Severe and hits its progress cap.

| Parameter | Value |
|---|---|
| Tiers | 4 (Mild / Moderate / Severe / Debilitating = Septic Shock) |
| Color | `0xCC4422` |
| Latch time | ~10–20 min after trigger |
| Worsening | Deterministic (1/9000/tick; thresholds at 2.5, 5.0, 7.5) |
| Recovery | None — progress never drains naturally |

**Debuffs (per tier, scaled by Severity debuffMult):**
- Max Health: −2 HP (ADDITION)

**Fever by tier:** Mild→Mild, Moderate→High, Severe→Severe, Debilitating→None (collapse).

**Symptoms:** Hypotension (→ breathlessness, from LIGHT), Sharp Pain at amplifier 2 (from LIGHT), Malaise, Mottled Skin (from MODERATE)

---

### 9. Multiple Organ Failure — `simplediseases:mof_staph`

Lethal complication of Debilitating Sepsis (Septic Shock).

| Parameter | Value |
|---|---|
| Tiers | 1 (Moderate only) |
| Latch time | ~5 min from Septic Shock activation |
| Decay (pre-latch) | 1/12000 per tick |
| Recovery | None without external cure |

**Symptoms:** None. Once latched: continuous Wither-like lethal damage until the player dies or is cured externally.

---

## Severity Scale

Five tiers. Diseases occupy a **window** centered on MODERATE.

| Tier | Duration × | Interval × | Debuff × | Roll Weight |
|---|---|---|---|---|
| LIGHT | 0.40 | 1.70 | 0.40 | 35 |
| MILD | 0.65 | 1.30 | 0.70 | 30 |
| MODERATE | 1.00 | 1.00 | 1.00 | 20 |
| SEVERE | 1.50 | 0.65 | 1.40 | 11 |
| DEBILITATING | 2.20 | 0.40 | 1.90 | 4 |

- **Duration ×** — symptom episode length multiplier vs Moderate.
- **Interval ×** — spacing between episodes (lower = more frequent at high tiers).
- **Debuff ×** — multiplier applied to attribute modifier amounts in `DiseaseMobEffect.modifier()`.
- **Roll Weight** — weighted toward milder outcomes. Immunodeficiency rolls twice and keeps the worst result.

Severity is rolled once when accumulation first latches. It can be reduced by treatment: 35% base chance per treatment event, decaying by 50% per prior successful reduction.

---

## Fever System

Fever is stored as a `double feverOffset` field on `DiseaseMobEffect`. It raises the player's required body temperature to begin recovery. It is **not** an attribute modifier and produces no `+N` numeric display.

| Level | Constant | CS BODY offset | Display color |
|---|---|---|---|
| Light Fever | `FEVER_LIGHT` | +10 | §e (yellow) |
| Mild Fever | `FEVER_MILD` | +20 | §6 (gold) |
| High Fever | `FEVER_HIGH` | +35 | §c (red) |
| Severe Fever | `FEVER_SEVERE` | +50 | §4 (dark red) |

**Tooltip:** `EffectRendererMixin` injects at RETURN of `EffectRenderer.getTooltipsWithDescription`, finds the `"potion.whenDrank"` header in the built list, and inserts `Component.translatable("simplediseases.fever.<level>")` immediately after it. This makes it appear first under "When Applied:" with the appropriate color and no number prefix.

**Gameplay gate:** `ColdSweatCompat.isWarmEnoughToRecover()` scans `player.getActiveEffects()` for `DiseaseMobEffect` instances, takes the maximum `getFeverOffset()`, and requires `BODY >= 0.0 + feverOffset`.

---

## Symptom System

Symptoms are short-duration `MobEffectInstance`s applied episodically while a disease is active. `SymptomEntry` fields: effect, action, optional sound, optional severity gate, optional amplifier. `SymptomService` manages pools and episode timers (scaled by the tier's durationMult/intervalMult).

### Symptom Actions

| Action | Effect when episode fires |
|---|---|
| `NONE` | Applies effect only |
| `DAMAGE` | Deals 0.5 damage |
| `DRAIN_FOOD` | Subtracts 1 food level |
| `NAUSEA` | Applies vanilla Nausea for the given duration |
| `BREATHLESS` | Applies an air deprivation tick |

### All Symptom Effects

| Effect | Display Name | Used by |
|---|---|---|
| `cough` | Coughing | Cold, Flu, RSV, Pneumonia, Bronchitis |
| `sneezing` | Runny Nose | Cold, Flu, RSV, Pneumonia, Bronchitis |
| `malaise` | Malaise | All diseases |
| `headache` | Headache | Flu, Norovirus, Pneumonia, Bronchitis |
| `sore_throat` | Sore Throat | Flu, Pneumonia, Bronchitis |
| `vomiting` | Vomiting | Flu (Severe+), Norovirus, Pneumonia, Bronchitis |
| `diarrhea` | Diarrhea | Norovirus |
| `stomach_cramps` | Stomach Cramps | Norovirus |
| `shortness_of_breath` | Shortness of Breath | Flu (Severe+), Pneumonia, Bronchitis |
| `bad_cough` | Bad Cough | Pneumonia |
| `cough_fit` | Coughing Fit | (registered, not yet assigned to a disease) |
| `sharp_pain` | Sharp Pain | Cellulitis, Sepsis |
| `hypotension` | Hypotension | Sepsis |
| `localized_redness` | Localized Redness | Cellulitis |
| `mottled_skin` | Mottled Skin | Sepsis (Moderate+) |

### Symptom-Driven Interactions (`SymptomEvents.java`)

| Trigger | Behavior |
|---|---|
| Sore Throat active + eating attempt | Cancels the eat action; shows actionbar message |
| Stomach Cramps active + small heal (≤ 1 HP, no Regeneration) | Cancels the heal (natural hunger regen only) |
| Sharp Pain amplifier ≥ 2 + sleep attempt | Blocks sleep; shows message |
| Any active norovirus effect (per tick) | Caps `FoodData.saturationLevel` at `disease_max_saturation` attribute value |

---

## Contagion System (`ContagionManager`)

One `Channel` per contagious disease. Cross-channel logic enforces single-disease mutual exclusion.

### Transmission Vectors

| Vector | Mechanism |
|---|---|
| Player → Player | Proximity within disease radius. Per-tick `playerTransmissionChance` roll → `transmissionBump` added to target's committed incubation. |
| Villager → Player | Same proximity. Villager exposure counter increments each tick; at `villagerExposureThreshold` the player receives a bump. |
| Player → Villager | Infected player in range increments villager's exposure counter; at threshold the villager receives a timed effect. |
| Villager → Villager | Every 60 s infected villagers roll `villagerVChance` to spread to nearby susceptible villagers. |
| Norovirus puddles | Infected villagers leave lingering puddles (50%/5 min). Players and villagers in puddles accumulate norovirus progress. |

### Committed Incubation Model

`pendingIncubation` + `pendingIncubationId` on `PlayerDiseaseState`. On contact: roll `[incubationMin, incubationMax]` (Immunodeficient: `[incubationMax, 2×incubationMax]`). The budget bleeds into that disease's progress over subsequent ticks even after the exposure ends. Only one incubation in-flight per player; subsequent contacts during an in-flight incubation add a direct `transmissionBump` on top without resetting.

### Mutual Exclusion

At any switch point below `NULLIFY_THRESHOLD` (0.05), a new incubation can clean-switch to a different disease in the same group. Above the threshold, the existing disease is protected.

---

## Acquisition Routes

### Damp / Wetness (`WetnessManager`)

Per-player `wetProgress` [0.0, 1.0]:

| Source | Rate |
|---|---|
| Fully underwater | +0.030/tick |
| Wading in water (< 70% wet) | +0.010/tick |
| Standing in rain | +0.0004/tick |
| On fire | −0.005/tick (also extinguishes fire) |
| Drying in air | −`getDryRate()`/tick (Cold Sweat WORLD trait; floor 0.00015) |

**Drenched effect tiers:**
- Amp 0 (Drenched I): wet ≥ 10%
- Amp 1 (Drenched II): wet ≥ 40%
- Amp 2 (Drenched III): wet ≥ 72%

Damp players in cold conditions accumulate respiratory disease (cold/RSV) progress.

### Windchill

`Chilling Wind` effect applied when the player is cold and exposed. Dry windchilled players can accumulate respiratory disease progress. Cold Sweat gating: `BODY − 10 < 0`.

### Disease Picker (Damp / Windchill exposure)

1. Try RSV: if Serene Seasons winter → 40%; otherwise 20%. Skip if flu season window is open.
2. Try Flu: only during active outbreak → 60%.
3. Default: Cold.

### Waterborne (`WaterborneManager`)

Norovirus only. Deterministic 32×32 infected regions via `worldSeed + position hash + epoch`. ~5% of regions permanently infected; Serene Seasons winter activates ~60% more. Zones rotate every 7 in-game days (168 000 ticks). Rates: +1/6000 progress/tick wading, ×2 submerged.

---

## Injury System (`InjuryManager`)

Flesh wounds created by bladed damage (swords, axes, tridents, arrows, shears) ≥ 4 HP on players with ≤ 7 armor toughness. Wounds progress through three severity phases of 150 s each.

| Effect | Trigger | Mechanism |
|---|---|---|
| Bleeding | Hit ≥ 5 HP from bladed weapon | Armor-tiered chance: 10% / 8% / 5% / 1% by toughness tier |
| Internal Bleeding | 15% chance on hit ≥ 5 HP | Deals 7.5 HP damage over 20 s |
| Blood Loss | Active bleeding wounds ≥ 3.5 total | Floors HP at 6 |
| Cellulitis seed | Per-second chance for each open wound phase | Per-phase chance depends on wound severity + immune state |

---

## Immunity System

Two mutually exclusive `MobEffect`s on the player:

| Effect | Roll behavior | Wound infection |
|---|---|---|
| `Boosted Immunity` | 1 weighted roll (normal); no bias toward worse tiers | ~3–5× reduced chance |
| `Immunodeficiency` | 2 weighted rolls, keep the worst (skews toward severe) | ~2× elevated chance; harsher incubation range |

**Post-recovery group immunity:** Recovering from any member of an exclusion group locks out the entire group for the disease's `immunityTicks`. All fresh-acquisition paths (damp, windchill, waterborne, contact) check the group immunity map before proceeding.

---

## Treatment & Cure

### Items

| Item | Effect | Cooldown |
|---|---|---|
| Warm broths (Mushroom Stew, Beetroot Soup, Rabbit Stew, Suspicious Stew) | −0.1 progress on all active diseases; clears active symptom episodes. Applies `Symptoms Managed` (5 min cooldown). | 5 min |
| Honey Bottle | −0.5 progress; attempts severity tier reduction (35% base, ×0.5 decay per prior reduction). Clears `Symptoms Managed`. Applies `Treatment Applied` (5 min cooldown). | 5 min |

### Sleep

Full sleep cycle calls `treat()` on all viral and complication diseases with a −1.0 progress reduction (equivalent to one honey bottle, without the tier reduction attempt). Displays "§7You feel better after resting."

### Natural Recovery

Viral diseases drain `ProgressComponent` at `recoveryRate`/tick once `inRecovery` is set. Recovery requires `ColdSweatCompat.isWarmEnoughToRecover()` to return true (BODY ≥ 0 + fever offset). Bacterial sepsis has no passive recovery rate.

---

## Flu Season System (`FluSeasonManager`)

Persisted via `FluSeasonData` (Forge `SavedData` on the overworld level).

| Feature | Detail |
|---|---|
| Season selection | Autumn (40% weight) or Winter (60% weight), re-rolled each in-game year |
| With Serene Seasons | Reads the actual `Season` enum; flu window = when that season is active |
| Without Serene Seasons | Vanilla fallback: third or fourth quarter of the 576 000-tick year |
| Outbreak roll | 60% chance when the flu window opens. Forced flu season always triggers an outbreak. |
| Effect on acquisition | Flu requires active outbreak to be catchable. RSV is suppressed during any open flu window (outbreak or not). |
| Force toggle | `/sdfluseason` — toggles forced flu season ON/OFF |

---

## Mod Compatibility

### Cold Sweat — `compileOnly`, detected at runtime

| Feature | With Cold Sweat | Without Cold Sweat |
|---|---|---|
| Drying rate | `Temperature.Trait.WORLD` (real heat sources included) | Biome temp + block light / 15 |
| Damp cold gate | `Trait.WORLD` < 1.0 | Same proxy |
| Windchill cold gate | `Trait.BODY` − 10 < 0 | Always true |
| Recovery gate | `Trait.BODY` ≥ 0 + feverOffset | (biome temp + light proxy) > feverOffset / 50 |
| Hot waterskin bonus | +1.0 to temperature calc | N/A |
| Goat fur armor | Tracked per armor slot | N/A |

Fever integration: `ColdSweatCompat.feverOffset(player)` iterates `getActiveEffects()`, reads `DiseaseMobEffect.getFeverOffset()`, returns the maximum.

### Serene Seasons — compile-time stubs in `src/main/java/sereneseasons/`

Excluded from the packaged JAR via `build.gradle` jar exclusion (`exclude 'sereneseasons/**'`). Detected via `SereneSeasonsCompat.LOADED`. Drives: RSV winter chance, norovirus reservoir density, flu season window resolution.

### JEED (Just Enough Effect Descriptions) — `compileOnly` + `runtimeOnly`

`EffectRendererMixin` (`@Mixin(value = EffectRenderer.class, remap = false)`) injects at `@At("RETURN")` of `getTooltipsWithDescription`. Locates the `"potion.whenDrank"` translatable component in the list, inserts the colored fever label at `index + 1`. `require = 0` makes the injection optional so the mod loads safely without JEED.

---

## Custom Attributes (`DiseaseAttributes`)

All registered to `EntityType.PLAYER`. All `RangedAttribute`, `setSyncable(true)`.

| Registry name | Display name | Default | Range | Applied by |
|---|---|---|---|---|
| `disease_max_saturation` | Max Saturation | 5.0 | 0–20 | Norovirus (ADDITION, reduces from 5.0) |
| `disease_knockback_factor` | Knockback Damage | 1.0 | 0–2 | Cellulitis (MULTIPLY_TOTAL) |
| `disease_block_break_speed` | Mining Speed | 1.0 | 0–2 | Respiratory diseases (MULTIPLY_TOTAL) |

`disease_block_break_speed` fills the role of `ForgeMod.BLOCK_BREAK_SPEED`, which did not exist in 1.20.1.

---

## ECS-lite Architecture

`PlayerDiseaseState` holds a `Map<ResourceLocation, DiseaseInstance>`. Each `DiseaseInstance` carries a component bag declared by its `DiseaseCategory`:

| Component | Purpose |
|---|---|
| `ProgressComponent` | Float accumulation `[0, cap]`, `inRecovery` flag, latched flag |
| `TierComponent` | Rolled `Severity` ordinal; reduction count for diminishing treatment odds |
| `SymptomPoolComponent` | Remaining unplayed symptoms for the current episode cycle |
| `SourceComponent` | For complications: source disease ID + expiry game tick |

New disease categories can add instances and components without modifying `PlayerDiseaseState`.

---

## NBT Persistence

`PlayerDiseaseState` saves/loads from a `CompoundTag` on the player.

| Key | Type | Content |
|---|---|---|
| `"wet"` | double | Wetness progress |
| `"diseases"` | ListTag | One compound per disease (`{id, components…}`) |
| `"groupImmunity"` | CompoundTag | Group name → expiry game tick |
| `"pendingIncubation"` | double | Remaining committed-incubation budget |
| `"pendingIncubationId"` | string | ResourceLocation of in-flight incubation disease |
| `"wasInInfectedWater"` | byte | Edge-detect flag for waterborne route |
| `"injury"` | CompoundTag | `PlayerInjuryState` subtag |

Contagion manager transmission state (villager exposure, player infector locks) is in-memory only and re-derived from carried effects on login.

---

## Debug Commands

| Command | Permission | Description |
|---|---|---|
| `/sddebugviral` | Any player | Toggles per-tick viral debug overlay (progress, severity, incubation). Toggle again to disable. |
| `/sddebugbacterial` | Any player | Toggles bacterial debug overlay (cellulitis, sepsis, MOF progress). |
| `/sdfluseason` | Any player | Force-toggles flu season ON/OFF. Forced season always produces an outbreak. |
| `/sdimmune boost` | Op (2) | Applies infinite Boosted Immunity (no particles, icon visible). |
| `/sdimmune deficient` | Op (2) | Applies infinite Immunodeficiency. |
| `/sdimmune clear` | Op (2) | Removes both immunity effects. |
| `/sdaccumulate <disease> <amount>` | Op (2) | Adds `amount` to the named disease's progress. Auto-seeds complication sources for pneumonia/bronchitis/sepsis. |
| `/sdinjury flesh_wound <0-2>` | Op (2) | Adds a flesh wound (0=mild, 1=moderate, 2=severe) to the player's injury state. |
| `/sdreservoir` | Op (2) | Teleports to the nearest infected norovirus reservoir within 48 blocks horizontal, 256 vertical. |
| `/sdcheck` | Any player | Lists disease status, exposure counters, and immunity for all villagers within 16 blocks. |

---

## Key Conventions

- **Never register directly to Forge registries.** Use the `DeferredRegister` instances (`DiseaseEffects.EFFECTS`, `DiseaseAttributes.ATTRIBUTES`, etc.).
- **New diseases belong in `DiseaseRegistry.bootstrap()`.** Call `register(new ViralDiseaseDef(…))` or the appropriate def type.
- **New MobEffect tiers use `DiseaseEffects.registerVariants()`.** It creates one `DiseaseMobEffect` per `Severity` in the window, scales modifiers by `debuffMult`, and calls `.fever()` if a fever offset is provided.
- **Fever offset is a field on `DiseaseMobEffect`, not an attribute.** `getFeverOffset()` is read by both `ColdSweatCompat` and `EffectRendererMixin`. Do not add fever as an attribute modifier.
- **Complications need a source.** `ComplicationDiseaseDef.triggeredBy` names the source disease path. Progress only accumulates while the source is active.
- **Mutual exclusion is per group.** Adding a new viral disease to `GROUP_VIRAL` inherits exclusion automatically from `ContagionManager`.
- **All compat calls have fallbacks.** Never call Cold Sweat or Serene Seasons classes directly outside their respective `*Compat` classes.
- **Symptom episodes are managed by `SymptomService`, not raw NBT.** Do not apply symptom effects manually in tick handlers; add them to the disease's `SymptomConfig`.
