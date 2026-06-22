# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
./gradlew build        # Compile and package JAR
./gradlew runClient    # Launch Minecraft client with the mod loaded
./gradlew runServer    # Launch dedicated server
./gradlew runData      # Regenerate data assets
```

There is no test framework. Verify features by running the client (`./gradlew runClient`).

## Development Principles

**After correctness, the highest priorities for every change are performance, multiplayer compatibility, cross-mod compatibility across the Forge ecosystem, and code optimization.** The mod targets **100+ mod modpacks** — code must be non-invasive and coexist with unknown mods. Treat these as hard constraints.

- **Performance:** Server-authoritative ticks; precompute per-tick values once in `DiseaseEvents`; reuse caches (`suppressedEpisodeSourcesCache`, windchill cache); avoid per-player allocations on hot paths.
- **Multiplayer:** Gameplay on server; world particles via `sendParticles` (visible to nearby players); `MobEffectInstance` sync for model/HUD state; custom packets only for local HUD (`BleedingSplatterPacket`).
- **Cross-mod compatibility:** **Forge-first, non-invasive.** Use Forge/vanilla extension points (`DeferredRegister`, event bus, effects, attributes, tags, `SavedData`, overlay events) before mixins or custom infrastructure. Keep mixins narrow and optional where possible (`require = 0`, `SimpleDiseasesMixinPlugin`). Optional mods only through `compat/` with offline fallbacks; runtime detection (`ModList`, `ColdSweatCompat.LOADED`). Never cancel or globally override other mods. No hard dependencies on other mods.
- **Code optimization:** Data-driven disease defs in `DiseaseRegistry`; centralize shared logic (`ColdSweatCompat`, `WorseningRoll`, `SymptomService`); remove redundancies; modular, reusable managers/services.

See **agents.md → Development Principles** for the full table and cross-mod decision order.

## Stack

Minecraft 1.20.1 · Forge 47.4.10 · Parchment mappings 2023.09.03-1.20.1 · Java 17 · Mixin 0.7+

Optional runtime deps (detected at runtime, never called directly): Cold Sweat (`compileOnly`), Serene Seasons (compile-time stubs under `src/main/java/sereneseasons/`, excluded from JAR), JEED (`compileOnly` + `runtimeOnly`).

## Architecture

The mod is structured around an **ECS-lite disease model**:

- `PlayerDiseaseState` — per-player root, holds `Map<ResourceLocation, DiseaseInstance>` plus wetness progress, pending incubation, accumulation-fatigue streak, group immunity, and injury state. Persisted via NBT on the player entity.
- `DiseaseInstance` — carries a component bag: `ProgressComponent`, `TierComponent`, `SymptomPoolComponent`, optional `SourceComponent` (complications only).
- `DiseaseContext` — per-tick record passed to categories: precomputed viral/bacterial recovery multipliers, `viralEnvironmentalAccumulating`, complication worsening group, suppressed episode sources.
- `DiseaseRegistry` — single source of truth for all disease definitions, partitioned into `viral()`, `bacterial()`, `complications()`, `contagious()`, `environmental()`. All definitions are created in `bootstrap()` at mod init.
- `DiseaseDef` subtypes — `ViralDiseaseDef`, `BacterialDiseaseDef`, `ComplicationDiseaseDef` — each carries a `DiseaseCategory` that determines which components are valid and what tick logic applies.
- `DiseaseEffects` / `DiseaseAttributes` — `DeferredRegister`-backed registries for `MobEffect` and `RangedAttribute`.
- `DiseaseMobEffect` — extends `SortedMobEffect` (Forge HUD sort order). Stores fever/shock offsets, optional `sharedIconId`, hidden max-health penalty modifiers at high fever/shock tiers, and chainable attribute modifiers for non-tier effects (Pain, Malaise). Read by `ColdSweatCompat`, `DiseaseTooltipHelper`, and `MobEffectTextureManagerMixin`.

### Event / Manager wiring (`SimpleDiseases` constructor)

1. `DiseaseAttributes.ATTRIBUTES` registered first (effects reference them in lambdas).
2. `DiseaseEffects.EFFECTS`, `DiseaseParticles.PARTICLES`, `DiseaseSounds.SOUNDS` registered on mod bus.
3. `DiseaseRegistry.bootstrap()` called (safe before registries populate — effects are referenced lazily).
4. `DiseaseEvents` (per-tick disease logic), `SdCommands`, `CureEvents`, `SymptomEvents` registered on the Forge event bus.

### Key subsystems

| Class / Package | Responsibility |
|---|---|
| `event/DiseaseEvents.java` | Per-tick: acquisition, progression, recovery multipliers, accum fatigue, symptoms, max-HP cap, debug overlay sync |
| `event/CureEvents.java` | Treatment items (broths, honey) + sleep recovery |
| `event/SymptomEvents.java` | Symptom-driven interactions (sore throat eat damage, stomach cramps block healing, pain blocks sleep) |
| `client/ClientDebugOverlay.java` | Multi-line HUD for `/sdviral` and `/sdbacterial` debug output |
| `client/tooltip/DiseaseTooltipHelper.java` | JEED disease tooltip: condition row + Symptoms section |
| `network/DebugOverlayPacket.java` | Server → client debug HUD sync |
| `status/EffectHudSort.java` | Reserved Forge `getSortOrder` bands for all mod effects |
| `status/def/PainProfile.java` | Severity-scaled persistent pain amplifiers per disease |
| `status/def/ConditionType.java` | Organ-system condition icons/labels for disease tooltips |
| `status/manager/AccumFatigueManager` | Anti-exploit damp/wind streak → warn / immunodeficiency while latched curable |
| `status/def/WorseningRoll` | Shared stochastic momentum: `min(1, 0.30 + 0.25 × worsenings)` |
| `status/manager/ContagionManager` | Player↔player, player↔villager, villager↔villager transmission; committed incubation model |
| `status/manager/WetnessManager` | `wetProgress` accumulation from rain/water; drives Damp respiratory exposure |
| `status/manager/WaterborneManager` | Deterministic 32×32 infected reservoir regions for Norovirus |
| `status/manager/InjuryManager` | Flesh wounds from bladed damage; seeds Cellulitis |
| `status/manager/FluSeasonManager` | Persisted flu season state (`FluSeasonData` via Forge `SavedData`) |
| `status/service/SymptomService` | Episode timers, symptom pools, duration/interval scaling by Severity tier |
| `compat/ColdSweatCompat` | All Cold Sweat temperature queries; recovery multiplier; fallback proxy when mod absent |
| `compat/SereneSeasonsCompat` | All Serene Seasons season queries; fallback when mod is absent |
| `mixin/EffectRendererMixin` | JEED disease tooltip post-process (`require = 0`; gated when JEED absent) |
| `mixin/ForgeGuiMixin` | Stomach cramps hunger-bar tint |
| `mixin/MobEffectTextureManagerMixin` | Redirects per-tier disease effect sprite lookup to `DiseaseMobEffect.sharedIconId` |

### Mutual exclusion groups

- `GROUP_VIRAL` (`"viral"`) — Cold, Flu, RSV, Norovirus. Only one active per player at a time.
- `GROUP_BACTERIAL` (`"bacterial"`) — Cellulitis. Only one active at a time.

Exclusion is enforced at the `NULLIFY_THRESHOLD` (0.05 progress) boundary inside `ContagionManager`.

## Disease Symptom Pools

All nine diseases and their hallmark / common / severe / persistent symptom configs are documented in **agents.md → Disease Symptom Pools**. Source of truth: `DiseaseRegistry.bootstrap()`.

Quick index:

| ID | Hallmarks | Common | Severe (ADV) | Persistent |
|---|---|---|---|---|
| `cold` | — | Cough, Sneezing, Headache, Sore Throat | — | Malaise |
| `flu` | — | Cough, Sneezing, Headache, Sore Throat | Vomiting, SOB, Tachypnea, Tachycardia | Malaise + Mild Pain |
| `rsv` | Wheezing | Cough, Sneezing | SOB, Tachypnea | Malaise |
| `norovirus` | — | Headache, Vomiting, Diarrhea, Stomach Cramps | — | Malaise |
| `pneumonia` | SOB, Bloody Coughing | 6 inherit-capable virals | Tachypnea, Tachycardia, Confusion | Malaise + Pain (PNEUMONIA) |
| `bronchitis` | SOB | 6 inherit-capable + Wheezing (inherit-only) | Tachypnea | Malaise + Mild Pain |
| `cellulitis_staph` | Localized Redness (static) | — | Hypotension, Tachycardia, Confusion | Malaise + Pain (CELLULITIS) |
| `sepsis_staph` | Hypotension | Redness (static, inherit-only), Confusion, Tachycardia, Tachypnea | SOB (inherit-only), Mottled Skin (static) | Malaise + Pain (SEPSIS) |
| `mof_staph` | — | — | — | Empty pool; lethal tick damage |

Sepsis exclusive pair: Localized Redness ↔ Mottled Skin. See agents.md for actions, timing, inheritance, and episode pacing.

## Key Conventions

- **Never register directly to Forge registries.** Use `DiseaseEffects.EFFECTS`, `DiseaseAttributes.ATTRIBUTES`, etc.
- **All new diseases belong in `DiseaseRegistry.bootstrap()`.** Call `register(new ViralDiseaseDef(…))` or the appropriate def type.
- **New MobEffect tiers use `DiseaseEffects.registerVariants()`.** Creates one `DiseaseMobEffect` per `Severity` with fever/shock metadata and hidden max-HP penalties only — no per-tier attack/mining/saturation debuffs. Each tier gets `.sharedIcon(path)` for shared HUD icons. All mod effects use `EffectHudSort` via `SortedMobEffect`.
- **MOF uses a single effect** `DiseaseEffects.MOF` registered as `mof` (disease id remains `mof_staph`).
- **Persistent pain** is `DiseaseEffects.PAIN` (`pain`), scaled by `PainProfile` in `PersistentEffects` — not fixed amplifiers per disease.
- **Fever is a `double feverOffset` field on `DiseaseMobEffect`, not a recovery attribute.** Levels: +0.05 / +0.10 / +0.15 / +0.20 (MC WORLD scale). High fever / shock tiers add hidden `MAX_HEALTH` penalties (−5% / −15% / −25%). `getFeverOffset()` is read by `ColdSweatCompat`; tooltips via `DiseaseTooltipHelper`. Never add fever as a visible attribute.
- **JEED tooltips:** condition row (`ConditionType`) + **Disease Symptoms:** section; strip vanilla When Applied block. Mixins gated by `SimpleDiseasesMixinPlugin` when JEED absent.
- **Debug HUD:** `/sdviral` and `/sdbacterial` toggle client overlay via `DebugOverlayPacket`.
- **Recovery multiplier:** `ColdSweatCompat.getRecoveryMultiplier(player, group, envAccumulating)`. Viral base floor 0.75; bacterial base floor 0.60; full fever on both. Precompute in `DiseaseEvents`; categories use `ctx.recoveryMultiplier(group)`. Viral env accum forces 0.0.
- **Worsening:** `WorseningRoll.chance(worsenings)` for momentum stochastic tier upgrades (viral, bacterial pre-cap, stochastic complications).
- **Accum fatigue:** `AccumFatigueManager.tick()` — 8 min warn, 15 min immunodeficiency; streak decays 1/tick when dry.
- **Complications require a source.** `ComplicationDiseaseDef.triggeredBy` names the source disease path; progress only accumulates while the source is active. Sepsis requires Cellulitis; MOF requires Sepsis.
- **All compat calls must go through `ColdSweatCompat` / `SereneSeasonsCompat`.** Never call Cold Sweat or Serene Seasons classes directly elsewhere.
- **Symptom episodes are managed by `SymptomService`.** Do not apply symptom `MobEffectInstance`s manually in tick handlers; add them to the disease's `SymptomConfig` instead.
- **Sleep blocked at Pain amp ≥ 3** (Severe / Excruciating).
- **Multiplayer visuals:** world particles and body shiver are server-broadcast / effect-synced; HUD overlays (bleeding splatter, debug overlay) are player-local packets only.
- **Serene Seasons compile stubs live under `src/main/java/sereneseasons/`.** They are excluded from the packaged JAR via the `exclude 'sereneseasons/**'` rule in `build.gradle`.