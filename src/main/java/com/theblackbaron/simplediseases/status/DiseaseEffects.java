package com.theblackbaron.simplediseases.status;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.def.Severity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public class DiseaseEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, SimpleDiseases.MOD_ID);

    // --- Per-tier disease variants ------------------------------------------------------------------
    private record BaseMod(Supplier<Attribute> attribute, String key, double amount, AttributeModifier.Operation op) {}

    // diseasePath -> (severity -> registered variant effect)
    private static final Map<String, EnumMap<Severity, RegistryObject<MobEffect>>> VARIANTS           = new HashMap<>();
    private static final Map<String, EnumMap<Severity, RegistryObject<MobEffect>>> PNEUMONIA_VARIANTS  = new HashMap<>();
    private static final Map<String, EnumMap<Severity, RegistryObject<MobEffect>>> BRONCHITIS_VARIANTS = new HashMap<>();
    // keyed by complication path e.g. "sepsis_staph"
    private static final Map<String, EnumMap<Severity, RegistryObject<MobEffect>>> SEPSIS_VARIANTS     = new HashMap<>();
    // keyed by complication path e.g. "mof_staph"
    private static final Map<String, EnumMap<Severity, RegistryObject<MobEffect>>> MOF_VARIANTS        = new HashMap<>();

    static {
        // Cold — 3 tiers (Mild/Moderate/Severe). Severe tier: light fever.
        registerDisease("cold", 0xf0c1ba, 3, List.of(
            new BaseMod(() -> Attributes.ATTACK_SPEED,            "attack_speed",      -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.BLOCK_BREAK_SPEED::get, "block_break_speed", -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
        ), Map.of(Severity.SEVERE, DiseaseMobEffect.FEVER_LIGHT));

        // Influenza — 4 tiers. Mild→mild fever, Moderate→high, Severe/Debilitating→severe.
        registerDisease("flu", 0xC8302E, 4, List.of(
            new BaseMod(() -> Attributes.ATTACK_SPEED,            "attack_speed",      -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.BLOCK_BREAK_SPEED::get, "block_break_speed", -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL)
        ), Map.of(
            Severity.MILD,         DiseaseMobEffect.FEVER_LIGHT,
            Severity.MODERATE,     DiseaseMobEffect.FEVER_MILD,
            Severity.SEVERE,       DiseaseMobEffect.FEVER_HIGH,
            Severity.DEBILITATING, DiseaseMobEffect.FEVER_HIGH
        ));

        // RSV — 3 tiers. Moderate→light fever, Severe→mild fever.
        registerDisease("rsv", 0xF2D027, 3, List.of(
            new BaseMod(() -> Attributes.ATTACK_SPEED,            "attack_speed",      -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.BLOCK_BREAK_SPEED::get, "block_break_speed", -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
        ), Map.of(
            Severity.MODERATE, DiseaseMobEffect.FEVER_LIGHT,
            Severity.SEVERE,   DiseaseMobEffect.FEVER_MILD
        ));

        // Norovirus — 3 tiers. Severe tier: light fever (same as cold).
        registerDisease("norovirus", 0x5B8C3E, 3, List.of(
            new BaseMod(DiseaseAttributes.MAX_SATURATION::get, "max_saturation", -2.0, AttributeModifier.Operation.ADDITION)
        ), Map.of(Severity.SEVERE, DiseaseMobEffect.FEVER_LIGHT));

        // Pneumonia — 4-tier viral complication. Same fever distribution as flu.
        Map<Severity, Double> pneumoniaFever = Map.of(
            Severity.MILD,         DiseaseMobEffect.FEVER_MILD,
            Severity.MODERATE,     DiseaseMobEffect.FEVER_HIGH,
            Severity.SEVERE,       DiseaseMobEffect.FEVER_SEVERE,
            Severity.DEBILITATING, DiseaseMobEffect.FEVER_SEVERE
        );
        List<BaseMod> pneumoniaMods = List.of(
            new BaseMod(() -> Attributes.ATTACK_SPEED,            "attack_speed",      -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.BLOCK_BREAK_SPEED::get, "block_break_speed", -0.20, AttributeModifier.Operation.MULTIPLY_TOTAL)
        );
        registerPneumonia("pneumonia_flu",  pneumoniaMods, pneumoniaFever);
        registerPneumonia("pneumonia_cold", pneumoniaMods, pneumoniaFever);
        registerPneumonia("pneumonia_rsv",  pneumoniaMods, pneumoniaFever);

        // Bronchitis — 3-tier viral complication. Same fever distribution as RSV.
        Map<Severity, Double> bronchitisFever = Map.of(
            Severity.MODERATE, DiseaseMobEffect.FEVER_LIGHT,
            Severity.SEVERE,   DiseaseMobEffect.FEVER_MILD
        );
        List<BaseMod> bronchitisMods = List.of(
            new BaseMod(() -> Attributes.ATTACK_SPEED,            "attack_speed",      -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.BLOCK_BREAK_SPEED::get, "block_break_speed", -0.15, AttributeModifier.Operation.MULTIPLY_TOTAL)
        );
        registerBronchitis("bronchitis_flu",  bronchitisMods, bronchitisFever);
        registerBronchitis("bronchitis_cold", bronchitisMods, bronchitisFever);
        registerBronchitis("bronchitis_rsv",  bronchitisMods, bronchitisFever);

        // Staph cellulitis — 3-tier. Mild→mild fever, Moderate→high, Severe→severe.
        registerDisease("cellulitis_staph", 0xCC4422, 3, List.of(
            new BaseMod(() -> Attributes.ATTACK_DAMAGE,          "attack_damage",    -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL),
            new BaseMod(DiseaseAttributes.KNOCKBACK_FACTOR::get, "knockback_factor", -0.10, AttributeModifier.Operation.MULTIPLY_TOTAL)
        ), Map.of(
            Severity.MILD,     DiseaseMobEffect.FEVER_LIGHT,
            Severity.MODERATE, DiseaseMobEffect.FEVER_MILD,
            Severity.SEVERE,   DiseaseMobEffect.FEVER_HIGH
        ));

        // Staph sepsis — 4-tier. MILD/MODERATE have fever; SEVERE/DEBILITATING enter septic shock
        // (body temperature is forced down toward hypothermia — fever is removed from SEVERE).
        List<BaseMod> sepsisMods = List.of(
            new BaseMod(() -> Attributes.MAX_HEALTH, "max_health", -2.0, AttributeModifier.Operation.ADDITION)
        );
        registerSepsis("sepsis_staph", sepsisMods,
            Map.of(
                Severity.MILD,     DiseaseMobEffect.FEVER_MILD,
                Severity.MODERATE, DiseaseMobEffect.FEVER_HIGH
            ),
            Map.of(
                Severity.SEVERE,       DiseaseMobEffect.SEPTIC_SHOCK_STRENGTH,
                Severity.DEBILITATING, DiseaseMobEffect.SEPTIC_SHOCK_STRENGTH
            )
        );

        // Multiple organ failure — 1-tier (MODERATE only). No fever; lethality via direct damage.
        registerMof("mof_staph");
    }

    // --- Registration helpers -----------------------------------------------------------------------

    private static EnumMap<Severity, RegistryObject<MobEffect>> registerVariants(
            String path, int color, int tierCount, List<BaseMod> baseMods) {
        return registerVariants(path, color, tierCount, baseMods, Map.of(), Map.of());
    }

    private static EnumMap<Severity, RegistryObject<MobEffect>> registerVariants(
            String path, int color, int tierCount, List<BaseMod> baseMods,
            Map<Severity, Double> feverOffsets) {
        return registerVariants(path, color, tierCount, baseMods, feverOffsets, Map.of());
    }

    private static EnumMap<Severity, RegistryObject<MobEffect>> registerVariants(
            String path, int color, int tierCount, List<BaseMod> baseMods,
            Map<Severity, Double> feverOffsets, Map<Severity, Double> shockOffsets) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = new EnumMap<>(Severity.class);
        for (Severity sev : Severity.window(tierCount)) {
            String regName = path + "_" + sev.id();
            double feverOffset = feverOffsets.getOrDefault(sev, 0.0);
            double shockOffset = shockOffsets.getOrDefault(sev, 0.0);
            RegistryObject<MobEffect> variant = EFFECTS.register(regName, () -> {
                DiseaseMobEffect effect = new DiseaseMobEffect(MobEffectCategory.HARMFUL, color);
                for (BaseMod m : baseMods) {
                    UUID uuid = UUID.nameUUIDFromBytes((regName + ":" + m.key()).getBytes(StandardCharsets.UTF_8));
                    effect.modifier(m.attribute().get(), uuid, m.amount() * sev.debuffMult, m.op());
                }
                if (feverOffset > 0.0) effect.fever(feverOffset);
                if (shockOffset > 0.0) effect.shock(shockOffset);
                return effect;
            });
            byTier.put(sev, variant);
        }
        return byTier;
    }

    private static void registerDisease(String path, int color, int tierCount, List<BaseMod> baseMods) {
        VARIANTS.put(path, registerVariants(path, color, tierCount, baseMods));
    }

    private static void registerDisease(String path, int color, int tierCount, List<BaseMod> baseMods,
                                         Map<Severity, Double> feverOffsets) {
        VARIANTS.put(path, registerVariants(path, color, tierCount, baseMods, feverOffsets));
    }

    private static void registerPneumonia(String path, List<BaseMod> baseMods) {
        PNEUMONIA_VARIANTS.put(path, registerVariants(path, 0x6B5876, 4, baseMods));
    }

    private static void registerPneumonia(String path, List<BaseMod> baseMods, Map<Severity, Double> feverOffsets) {
        PNEUMONIA_VARIANTS.put(path, registerVariants(path, 0x6B5876, 4, baseMods, feverOffsets));
    }

    private static void registerBronchitis(String path, List<BaseMod> baseMods) {
        BRONCHITIS_VARIANTS.put(path, registerVariants(path, 0x8A6A42, 3, baseMods));
    }

    private static void registerBronchitis(String path, List<BaseMod> baseMods, Map<Severity, Double> feverOffsets) {
        BRONCHITIS_VARIANTS.put(path, registerVariants(path, 0x8A6A42, 3, baseMods, feverOffsets));
    }

    private static void registerSepsis(String path, List<BaseMod> baseMods) {
        SEPSIS_VARIANTS.put(path, registerVariants(path, 0x5C2B5C, 4, baseMods));
    }

    private static void registerSepsis(String path, List<BaseMod> baseMods, Map<Severity, Double> feverOffsets) {
        SEPSIS_VARIANTS.put(path, registerVariants(path, 0x5C2B5C, 4, baseMods, feverOffsets));
    }

    private static void registerSepsis(String path, List<BaseMod> baseMods,
                                        Map<Severity, Double> feverOffsets, Map<Severity, Double> shockOffsets) {
        SEPSIS_VARIANTS.put(path, registerVariants(path, 0x5C2B5C, 4, baseMods, feverOffsets, shockOffsets));
    }

    private static void registerMof(String path) {
        MOF_VARIANTS.put(path, registerVariants(path, 0x1A0A2A, 1, List.of()));
    }

    // --- Public variant lookups ---------------------------------------------------------------------

    public static RegistryObject<MobEffect> variant(String diseasePath, Severity severity) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = VARIANTS.get(diseasePath);
        return byTier == null ? null : byTier.get(severity);
    }

    public static RegistryObject<MobEffect> complicationVariant(ResourceLocation complicationId, ResourceLocation sourceId, Severity severity) {
        return switch (complicationId.getPath()) {
            case "bronchitis"   -> bronchitisVariant(sourceId, severity);
            case "sepsis_staph" -> sepsisVariant(sourceId, severity);
            case "mof_staph"    -> mofVariant(sourceId, severity);
            default             -> pneumoniaVariant(sourceId, severity);
        };
    }

    public static RegistryObject<MobEffect> mofVariant(ResourceLocation sourceId, Severity severity) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = MOF_VARIANTS.get(mofPath(sourceId));
        RegistryObject<MobEffect> variant = byTier == null ? null : byTier.get(Severity.MODERATE);
        if (variant != null) return variant;
        return MOF_VARIANTS.get("mof_staph").get(Severity.MODERATE);
    }

    public static RegistryObject<MobEffect> pneumoniaVariant(ResourceLocation sourceId, Severity severity) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = PNEUMONIA_VARIANTS.get(pneumoniaPath(sourceId));
        RegistryObject<MobEffect> variant = byTier == null ? null : byTier.get(severity);
        if (variant != null) return variant;
        return PNEUMONIA_VARIANTS.get("pneumonia_flu").get(Severity.MODERATE);
    }

    public static RegistryObject<MobEffect> bronchitisVariant(ResourceLocation sourceId, Severity severity) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = BRONCHITIS_VARIANTS.get(bronchitisPath(sourceId));
        RegistryObject<MobEffect> variant = byTier == null ? null : byTier.get(severity);
        if (variant != null) return variant;
        return BRONCHITIS_VARIANTS.get("bronchitis_rsv").get(Severity.MODERATE);
    }

    public static RegistryObject<MobEffect> sepsisVariant(ResourceLocation sourceId, Severity severity) {
        String key = sepsisPath(sourceId);
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = SEPSIS_VARIANTS.get(key);
        RegistryObject<MobEffect> variant = byTier == null ? null : byTier.get(severity);
        if (variant != null) return variant;
        return SEPSIS_VARIANTS.get("sepsis_staph").get(Severity.MILD);
    }

    public static boolean hasPneumoniaVariant(LivingEntity e, ResourceLocation sourceId) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = PNEUMONIA_VARIANTS.get(pneumoniaPath(sourceId));
        if (byTier == null) return false;
        for (RegistryObject<MobEffect> effect : byTier.values()) {
            if (e.hasEffect(effect.get())) return true;
        }
        return false;
    }

    public static boolean hasComplicationVariantForSource(LivingEntity e, ResourceLocation sourceId) {
        return hasPneumoniaVariant(e, sourceId) || hasBronchitisVariant(e, sourceId);
    }

    public static boolean hasBronchitisVariant(LivingEntity e, ResourceLocation sourceId) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = BRONCHITIS_VARIANTS.get(bronchitisPath(sourceId));
        if (byTier == null) return false;
        for (RegistryObject<MobEffect> effect : byTier.values()) {
            if (e.hasEffect(effect.get())) return true;
        }
        return false;
    }

    public static boolean hasAnyPneumonia(LivingEntity e) {
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : PNEUMONIA_VARIANTS.values()) {
            for (RegistryObject<MobEffect> effect : byTier.values()) {
                if (e.hasEffect(effect.get())) return true;
            }
        }
        return false;
    }

    public static boolean hasShiveringDisease(LivingEntity e) {
        for (net.minecraft.world.effect.MobEffectInstance inst : e.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme
                    && dme.getFeverOffset() >= DiseaseMobEffect.FEVER_HIGH) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSepticShock(LivingEntity e) {
        for (net.minecraft.world.effect.MobEffectInstance inst : e.getActiveEffects()) {
            if (inst.getEffect() instanceof DiseaseMobEffect dme && dme.getShockOffset() > 0.0)
                return true;
        }
        return false;
    }

    private static boolean hasModerateOrWorsePneumonia(LivingEntity e) {
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : PNEUMONIA_VARIANTS.values()) {
            for (Map.Entry<Severity, RegistryObject<MobEffect>> entry : byTier.entrySet()) {
                if (entry.getKey().ordinal() >= Severity.MODERATE.ordinal()
                        && e.hasEffect(entry.getValue().get())) return true;
            }
        }
        return false;
    }

    private static boolean hasSevereBronchitis(LivingEntity e) {
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : BRONCHITIS_VARIANTS.values()) {
            RegistryObject<MobEffect> severe = byTier.get(Severity.SEVERE);
            if (severe != null && e.hasEffect(severe.get())) return true;
        }
        return false;
    }

    private static boolean hasSevereSepsis(LivingEntity e) {
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : SEPSIS_VARIANTS.values()) {
            RegistryObject<MobEffect> severe = byTier.get(Severity.SEVERE);
            if (severe != null && e.hasEffect(severe.get())) return true;
        }
        return false;
    }

    private static boolean hasSevereOrWorseVariant(LivingEntity e, String diseasePath) {
        EnumMap<Severity, RegistryObject<MobEffect>> byTier = VARIANTS.get(diseasePath);
        if (byTier == null) return false;
        for (Map.Entry<Severity, RegistryObject<MobEffect>> entry : byTier.entrySet()) {
            if (entry.getKey().ordinal() >= Severity.SEVERE.ordinal() && e.hasEffect(entry.getValue().get())) {
                return true;
            }
        }
        return false;
    }

    public static void removePneumonia(LivingEntity e) {
        removeOtherPneumoniaVariants(e, null, null);
    }

    public static void removeBronchitis(LivingEntity e) {
        removeOtherBronchitisVariants(e, null, null);
    }

    public static void removeSepsis(LivingEntity e) {
        removeOtherSepsisVariants(e, null, null);
    }

    public static void removeComplication(LivingEntity e, ResourceLocation complicationId) {
        switch (complicationId.getPath()) {
            case "bronchitis"   -> removeBronchitis(e);
            case "sepsis_staph" -> removeSepsis(e);
            case "mof_staph"    -> removeMof(e);
            default             -> removePneumonia(e);
        }
    }

    public static void removeOtherComplicationVariants(LivingEntity e, ResourceLocation complicationId,
                                                        ResourceLocation keepSourceId, Severity keepSeverity) {
        switch (complicationId.getPath()) {
            case "bronchitis"   -> removeOtherBronchitisVariants(e, keepSourceId, keepSeverity);
            case "sepsis_staph" -> removeOtherSepsisVariants(e, keepSourceId, keepSeverity);
            case "mof_staph"    -> removeOtherMofVariants(e, keepSourceId, keepSeverity);
            default             -> removeOtherPneumoniaVariants(e, keepSourceId, keepSeverity);
        }
    }

    public static void removeMof(LivingEntity e) {
        removeOtherMofVariants(e, null, null);
    }

    public static void removeOtherMofVariants(LivingEntity e, ResourceLocation keepSourceId, Severity keepSeverity) {
        MobEffect keep = keepSourceId == null || keepSeverity == null
                ? null : mofVariant(keepSourceId, keepSeverity).get();
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : MOF_VARIANTS.values()) {
            for (RegistryObject<MobEffect> effect : byTier.values()) {
                MobEffect mobEffect = effect.get();
                if (mobEffect != keep && e.hasEffect(mobEffect)) e.removeEffect(mobEffect);
            }
        }
    }

    public static void removeOtherPneumoniaVariants(LivingEntity e, ResourceLocation keepSourceId, Severity keepSeverity) {
        MobEffect keep = keepSourceId == null || keepSeverity == null
                ? null : pneumoniaVariant(keepSourceId, keepSeverity).get();
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : PNEUMONIA_VARIANTS.values()) {
            for (RegistryObject<MobEffect> effect : byTier.values()) {
                MobEffect mobEffect = effect.get();
                if (mobEffect != keep && e.hasEffect(mobEffect)) e.removeEffect(mobEffect);
            }
        }
    }

    public static void removeOtherBronchitisVariants(LivingEntity e, ResourceLocation keepSourceId, Severity keepSeverity) {
        MobEffect keep = keepSourceId == null || keepSeverity == null
                ? null : bronchitisVariant(keepSourceId, keepSeverity).get();
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : BRONCHITIS_VARIANTS.values()) {
            for (RegistryObject<MobEffect> effect : byTier.values()) {
                MobEffect mobEffect = effect.get();
                if (mobEffect != keep && e.hasEffect(mobEffect)) e.removeEffect(mobEffect);
            }
        }
    }

    public static void removeOtherSepsisVariants(LivingEntity e, ResourceLocation keepSourceId, Severity keepSeverity) {
        MobEffect keep = keepSourceId == null || keepSeverity == null
                ? null : sepsisVariant(keepSourceId, keepSeverity).get();
        for (EnumMap<Severity, RegistryObject<MobEffect>> byTier : SEPSIS_VARIANTS.values()) {
            for (RegistryObject<MobEffect> effect : byTier.values()) {
                MobEffect mobEffect = effect.get();
                if (mobEffect != keep && e.hasEffect(mobEffect)) e.removeEffect(mobEffect);
            }
        }
    }

    private static String pneumoniaPath(ResourceLocation sourceId) {
        return switch (sourceId.getPath()) {
            case "cold" -> "pneumonia_cold";
            case "rsv"  -> "pneumonia_rsv";
            default     -> "pneumonia_flu";
        };
    }

    private static String bronchitisPath(ResourceLocation sourceId) {
        return switch (sourceId.getPath()) {
            case "cold" -> "bronchitis_cold";
            case "flu"  -> "bronchitis_flu";
            default     -> "bronchitis_rsv";
        };
    }

    private static String sepsisPath(ResourceLocation sourceId) {
        return switch (sourceId.getPath()) {
            case "cellulitis_staph" -> "sepsis_staph";
            default                 -> "sepsis_staph";
        };
    }

    private static String mofPath(ResourceLocation sourceId) {
        return "mof_staph";
    }

    // --- Indicators, symptoms, immune tiers ---------------------------------------------------------
    public static final RegistryObject<MobEffect> DAMP =
            EFFECTS.register("damp", DampEffect::new);

    public static final RegistryObject<MobEffect> CHILLY_WIND =
            EFFECTS.register("chilly_wind", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x9DBEE0) {});

    public static final RegistryObject<MobEffect> COUGH =
            EFFECTS.register("cough", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xA0522D) {});

    public static final RegistryObject<MobEffect> SNEEZING =
            EFFECTS.register("sneezing", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xF5DEB3) {});

    public static final RegistryObject<MobEffect> MALAISE =
            EFFECTS.register("malaise", MalaiseEffect::new);

    public static final RegistryObject<MobEffect> VOMITING =
            EFFECTS.register("vomiting", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x6B8E23) {});

    public static final RegistryObject<MobEffect> SHORTNESS_OF_BREATH =
            EFFECTS.register("shortness_of_breath", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x7FA8C9) {});

    public static final RegistryObject<MobEffect> HYPOTENSION =
            EFFECTS.register("hypotension", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x7090B0) {});

    public static final RegistryObject<MobEffect> HEADACHE =
            EFFECTS.register("headache", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x9B4B4B) {});

    public static final RegistryObject<MobEffect> SORE_THROAT =
            EFFECTS.register("sore_throat", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xC76B5A) {});

    public static final RegistryObject<MobEffect> STOMACH_CRAMPS =
            EFFECTS.register("stomach_cramps", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x7A5C3E) {});

    public static final RegistryObject<MobEffect> DIARRHEA =
            EFFECTS.register("diarrhea", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x6E4B1F) {});

    public static final RegistryObject<MobEffect> MOTTLED_SKIN =
            EFFECTS.register("mottled_skin", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x9966AA) {});

    public static final RegistryObject<MobEffect> BAD_COUGH =
            EFFECTS.register("bad_cough", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x8B3A3A) {});

    public static final RegistryObject<MobEffect> COUGH_FIT =
            EFFECTS.register("cough_fit", CoughFitEffect::new);

    public static final RegistryObject<MobEffect> BLEEDING =
            EFFECTS.register("bleeding", BleedingEffect::new);

    public static final RegistryObject<MobEffect> FLESH_WOUND =
            EFFECTS.register("flesh_wound", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x7A1B14) {});

    public static final RegistryObject<MobEffect> INTERNAL_BLEEDING =
            EFFECTS.register("internal_bleeding", InternalBleedingEffect::new);

    public static final RegistryObject<MobEffect> BLOOD_LOSS =
            EFFECTS.register("blood_loss", BloodLossEffect::new);

    public static final RegistryObject<MobEffect> LOCALIZED_REDNESS =
            EFFECTS.register("localized_redness", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0xCC4422) {});

    public static final RegistryObject<MobEffect> SHARP_PAIN =
            EFFECTS.register("sharp_pain", () -> new DiseaseMobEffect(MobEffectCategory.HARMFUL, 0xAA2244)
                    .modifier(Attributes.ATTACK_SPEED,                  UUID.nameUUIDFromBytes("sharp_pain:attack_speed".getBytes(StandardCharsets.UTF_8)),       -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .modifier(Attributes.ATTACK_DAMAGE,                 UUID.nameUUIDFromBytes("sharp_pain:attack_damage".getBytes(StandardCharsets.UTF_8)),      -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .modifier(DiseaseAttributes.BLOCK_BREAK_SPEED.get(), UUID.nameUUIDFromBytes("sharp_pain:block_break_speed".getBytes(StandardCharsets.UTF_8)), -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .modifier(DiseaseAttributes.KNOCKBACK_FACTOR.get(),  UUID.nameUUIDFromBytes("sharp_pain:knockback_factor".getBytes(StandardCharsets.UTF_8)),  -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL)
                    .modifier(Attributes.MOVEMENT_SPEED,                UUID.nameUUIDFromBytes("sharp_pain:movement_speed".getBytes(StandardCharsets.UTF_8)),     -0.05, AttributeModifier.Operation.MULTIPLY_TOTAL));

    public static final RegistryObject<MobEffect> SYMPTOMS_MANAGED =
            EFFECTS.register("symptoms_managed", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0xFFD080) {});

    public static final RegistryObject<MobEffect> TREATMENT_APPLIED =
            EFFECTS.register("treatment_applied", () -> new MobEffect(MobEffectCategory.BENEFICIAL, 0x80D0FF) {});

    public static final RegistryObject<MobEffect> IMMUNE_DEFICIENCY =
            EFFECTS.register("immune_deficiency", ImmuneDeficiencyEffect::new);

    public static final RegistryObject<MobEffect> IMMUNE =
            EFFECTS.register("immune", ImmuneEffect::new);
}
