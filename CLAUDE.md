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

## Stack

Minecraft 1.20.1 · Forge 47.4.10 · Parchment mappings 2023.09.03-1.20.1 · Java 17 · Mixin 0.7+

Optional runtime deps (detected at runtime, never called directly): Cold Sweat (`compileOnly`), Serene Seasons (compile-time stubs under `src/main/java/sereneseasons/`, excluded from JAR), JEED (`compileOnly` + `runtimeOnly`).

## Architecture

The mod is structured around an **ECS-lite disease model**:

- `PlayerDiseaseState` — per-player root, holds `Map<ResourceLocation, DiseaseInstance>` plus wetness progress, pending incubation, and injury state. Persisted via NBT on the player entity.
- `DiseaseInstance` — carries a component bag: `ProgressComponent`, `TierComponent`, `SymptomPoolComponent`, optional `SourceComponent` (complications only).
- `DiseaseRegistry` — single source of truth for all disease definitions, partitioned into `viral()`, `bacterial()`, `complications()`, `contagious()`, `environmental()`. All definitions are created in `bootstrap()` at mod init.
- `DiseaseDef` subtypes — `ViralDiseaseDef`, `BacterialDiseaseDef`, `ComplicationDiseaseDef` — each carries a `DiseaseCategory` that determines which components are valid and what tick logic applies.
- `DiseaseEffects` / `DiseaseAttributes` — `DeferredRegister`-backed registries for `MobEffect` and `RangedAttribute`.
- `DiseaseMobEffect` — `MobEffect` subclass that stores ordered attribute modifiers, optional `sharedIconId` (tier disease HUD icon remap), and a `feverOffset` double (not an attribute modifier). Read by `ColdSweatCompat` and `EffectRendererMixin`.

### Event / Manager wiring (`SimpleDiseases` constructor)

1. `DiseaseAttributes.ATTRIBUTES` registered first (effects reference them in lambdas).
2. `DiseaseEffects.EFFECTS`, `DiseaseParticles.PARTICLES`, `DiseaseSounds.SOUNDS` registered on mod bus.
3. `DiseaseRegistry.bootstrap()` called (safe before registries populate — effects are referenced lazily).
4. `DiseaseEvents` (per-tick disease logic), `SdCommands`, `CureEvents`, `SymptomEvents` registered on the Forge event bus.

### Key subsystems

| Class / Package | Responsibility |
|---|---|
| `event/DiseaseEvents.java` | Per-tick: acquisition, progression, symptoms, complication gating |
| `event/CureEvents.java` | Treatment items (broths, honey) + sleep recovery |
| `event/SymptomEvents.java` | Symptom-driven interactions (sore throat blocks eating, stomach cramps block healing, pain blocks sleep) |
| `status/manager/ContagionManager` | Player↔player, player↔villager, villager↔villager transmission; committed incubation model |
| `status/manager/WetnessManager` | `wetProgress` accumulation from rain/water; drives Damp respiratory exposure |
| `status/manager/WaterborneManager` | Deterministic 32×32 infected reservoir regions for Norovirus |
| `status/manager/InjuryManager` | Flesh wounds from bladed damage; seeds Cellulitis |
| `status/manager/FluSeasonManager` | Persisted flu season state (`FluSeasonData` via Forge `SavedData`) |
| `status/service/SymptomService` | Episode timers, symptom pools, duration/interval scaling by Severity tier |
| `compat/ColdSweatCompat` | All Cold Sweat temperature queries; fallback proxy for when mod is absent |
| `compat/SereneSeasonsCompat` | All Serene Seasons season queries; fallback when mod is absent |
| `mixin/EffectRendererMixin` | JEED tooltip injection — inserts fever label under "When Applied:"; `require = 0` makes it optional |
| `mixin/MobEffectTextureManagerMixin` | Redirects per-tier disease effect sprite lookup to `DiseaseMobEffect.sharedIconId` |

### Mutual exclusion groups

- `GROUP_VIRAL` (`"viral"`) — Cold, Flu, RSV, Norovirus. Only one active per player at a time.
- `GROUP_BACTERIAL` (`"bacterial"`) — Cellulitis. Only one active at a time.

Exclusion is enforced at the `NULLIFY_THRESHOLD` (0.05 progress) boundary inside `ContagionManager`.

## Key Conventions

- **Never register directly to Forge registries.** Use `DiseaseEffects.EFFECTS`, `DiseaseAttributes.ATTRIBUTES`, etc.
- **All new diseases belong in `DiseaseRegistry.bootstrap()`.** Call `register(new ViralDiseaseDef(…))` or the appropriate def type.
- **New MobEffect tiers use `DiseaseEffects.registerVariants()`.** It creates one `DiseaseMobEffect` per `Severity` in the window and scales modifiers by `debuffMult`. Each tier gets `.sharedIcon(path)` so all severities use `textures/mob_effect/<path>.png`.
- **MOF uses a single effect** `DiseaseEffects.MOF` registered as `mof` (disease id remains `mof_staph`).
- **Persistent pain** is `DiseaseEffects.PAIN` (`pain`), not per-tier variants.
- **Fever is a `double feverOffset` field on `DiseaseMobEffect`, not an attribute modifier.** `getFeverOffset()` is read by `ColdSweatCompat.feverOffset()` and `EffectRendererMixin`. Never add fever as an attribute.
- **Complications require a source.** `ComplicationDiseaseDef.triggeredBy` names the source disease path; progress only accumulates while the source is active. Sepsis requires Cellulitis; MOF requires Sepsis.
- **All compat calls must go through `ColdSweatCompat` / `SereneSeasonsCompat`.** Never call Cold Sweat or Serene Seasons classes directly elsewhere.
- **Symptom episodes are managed by `SymptomService`.** Do not apply symptom `MobEffectInstance`s manually in tick handlers; add them to the disease's `SymptomConfig` instead.
- **Serene Seasons compile stubs live under `src/main/java/sereneseasons/`.** They are excluded from the packaged JAR via the `exclude 'sereneseasons/**'` rule in `build.gradle`.