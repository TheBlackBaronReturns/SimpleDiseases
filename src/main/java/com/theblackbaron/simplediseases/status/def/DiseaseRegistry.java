package com.theblackbaron.simplediseases.status.def;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import com.theblackbaron.simplediseases.sound.DiseaseSounds;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DiseaseRegistry {
    private DiseaseRegistry() {}

    public static final ResourceLocation COLD             = new ResourceLocation(SimpleDiseases.MOD_ID, "cold");
    public static final ResourceLocation FLU              = new ResourceLocation(SimpleDiseases.MOD_ID, "flu");
    public static final ResourceLocation RSV              = new ResourceLocation(SimpleDiseases.MOD_ID, "rsv");
    public static final ResourceLocation NOROVIRUS        = new ResourceLocation(SimpleDiseases.MOD_ID, "norovirus");
    public static final ResourceLocation PNEUMONIA        = new ResourceLocation(SimpleDiseases.MOD_ID, "pneumonia");
    public static final ResourceLocation BRONCHITIS       = new ResourceLocation(SimpleDiseases.MOD_ID, "bronchitis");
    public static final ResourceLocation CELLULITIS_STAPH = new ResourceLocation(SimpleDiseases.MOD_ID, "cellulitis_staph");
    public static final ResourceLocation SEPSIS_STAPH     = new ResourceLocation(SimpleDiseases.MOD_ID, "sepsis_staph");
    public static final ResourceLocation MOF_STAPH        = new ResourceLocation(SimpleDiseases.MOD_ID, "mof_staph");

    /** Pathogen type values — ColdSweat recovery-warmth thresholds, environmental complication-worsening
     *  gate, and debug-panel bucketing. NOT the mutual-exclusion key; see {@link DiseaseDef#exclusionGroup()}. */
    public static final String GROUP_VIRAL    = "viral";
    public static final String GROUP_BACTERIAL = "bacterial";

    public static final long VIRAL_IMMUNITY_TICKS = 20L * 60 * 20; // 20 minutes

    /** Max individual diseases a player may have active at once. Complications (pneumonia, bronchitis,
     *  sepsis, MOF) only count once latched, not while still pre-latch accumulating — see
     *  {@link com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState#activeDiseaseCount()}. */
    public static final int MAX_CONCURRENT_DISEASES = 3;

    private static final Map<ResourceLocation, DiseaseDef> BY_ID = new LinkedHashMap<>();

    private static List<DiseaseDef> allList           = List.of();
    private static List<DiseaseDef> viralList         = List.of();
    private static List<DiseaseDef> contagiousList    = List.of();
    private static List<DiseaseDef> environmentalList = List.of();
    private static List<DiseaseDef> complicationList  = List.of();
    private static List<DiseaseDef> bacterialList     = List.of();

    private static final List<Double> DEFAULT_THRESHOLDS = List.of(0.10, 0.40, 0.70);

    private static SymptomConfig symptomConfig(List<SymptomEntry> hallmarks, List<SymptomEntry> common,
                                                List<SymptomEntry> severe,
                                                int minInterval, int maxInterval, int minDuration, int maxDuration,
                                                PersistentEffects persistent) {
        return symptomConfig(hallmarks, common, severe, List.of(), minInterval, maxInterval,
                minDuration, maxDuration, persistent);
    }

    private static SymptomConfig symptomConfig(List<SymptomEntry> hallmarks, List<SymptomEntry> common,
                                                List<SymptomEntry> severe,
                                                List<SymptomSupersedes> exclusivePairs,
                                                int minInterval, int maxInterval, int minDuration, int maxDuration,
                                                PersistentEffects persistent) {
        return new SymptomConfig(hallmarks, common, severe, exclusivePairs, DEFAULT_THRESHOLDS,
                minInterval, maxInterval, minDuration, maxDuration, persistent);
    }

    public static void bootstrap() {
        if (!BY_ID.isEmpty()) return;

        register(new ViralDiseaseDef(
            COLD, 3, () -> DiseaseParticles.COLD.get(), ConditionType.RESPIRATORY, GROUP_VIRAL,
            2.0, 1.0, 0.000030, 24000L,
            symptomConfig(
                List.of(),
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,    SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING, SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE)
                ),
                List.of(),
                20 * 120, 20 * 300, 20 * 30, 20 * 60,
                PersistentEffects.malaiseOnly()
            ),
            new ViralContagion(6.0, 0.167f, 0.05, 115, 0.10f, 0.25f, 0.35f, 24000, 24000, 0.05f, 0.0f, true, true),
            AcquisitionRule.defaultDisease(),
            "message.simplediseases.caught_cold", "message.simplediseases.cured_cold",
            0.1, 0.5
        ));

        register(new ViralDiseaseDef(
            FLU, 4, () -> DiseaseParticles.FLU.get(), ConditionType.RESPIRATORY, GROUP_VIRAL,
            10.0, 1.0, 0.000030, 48000L,
            symptomConfig(
                List.of(),
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,       SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE)
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.VOMITING, SymptomAction.DRAIN_FOOD,
                            () -> DiseaseSounds.VOMIT.get(), SymptomBand.ADVANCED),
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,
                            () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), SymptomBand.ADVANCED, 200),
                    new SymptomEntry(DiseaseEffects.TACHYPNEA, SymptomAction.NONE,
                            () -> DiseaseSounds.RAPID_BREATHING.get(), SymptomBand.ADVANCED),
                    new SymptomEntry(DiseaseEffects.TACHYCARDIA, SymptomAction.NONE,
                            () -> DiseaseSounds.HEARTBEAT.get(), SymptomBand.ADVANCED)
                ),
                20 * 60, 20 * 180, 20 * 45, 20 * 90,
                PersistentEffects.withPain(PainProfile.MILD_FLAT)
            ),
            new ViralContagion(6.0, 0.334f, 0.10, 20, 0.20f, 0.70f, 0.80f, 24000, 24000, 0.0f, 0.0f, true, true),
            new AcquisitionRule(false, true, false, 0.6, 0.0),
            "message.simplediseases.caught_flu", "message.simplediseases.cured_flu",
            0.5, 1.0
        ));

        register(new ViralDiseaseDef(
            RSV, 3, () -> DiseaseParticles.RSV.get(), ConditionType.RESPIRATORY, GROUP_VIRAL,
            10.0, 1.0, 0.000030, 36000L,
            symptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.WHEEZING, SymptomAction.NONE, () -> DiseaseSounds.WHEEZING_SOUND.get())
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,    SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING, SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get())
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,
                            () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), SymptomBand.ADVANCED, 200),
                    new SymptomEntry(DiseaseEffects.TACHYPNEA, SymptomAction.NONE,
                            () -> DiseaseSounds.RAPID_BREATHING.get(), SymptomBand.ADVANCED)
                ),
                20 * 90, 20 * 210, 20 * 40, 20 * 80,
                PersistentEffects.malaiseOnly()
            ),
            new ViralContagion(6.0, 0.334f, 0.10, 20, 0.20f, 0.70f, 0.80f, 24000, 24000, 0.0f, 0.15f, true, true),
            new AcquisitionRule(false, false, true, 0.20, 0.40),
            "message.simplediseases.caught_rsv", "message.simplediseases.cured_rsv",
            0.1, 0.5
        ));

        register(new ViralDiseaseDef(
            NOROVIRUS, 3, () -> DiseaseParticles.NOROVIRUS.get(), ConditionType.GI, GROUP_VIRAL,
            2.0, 1.0, 0.00006, 6000L,
            symptomConfig(
                List.of(),
                List.of(
                    new SymptomEntry(DiseaseEffects.HEADACHE,       SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.VOMITING,       SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get()),
                    new SymptomEntry(DiseaseEffects.DIARRHEA,       SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.DIARRHEA.get()),
                    new SymptomEntry(DiseaseEffects.STOMACH_CRAMPS, SymptomAction.NONE, () -> DiseaseSounds.STOMACH_CRAMPS.get())
                ),
                List.of(),
                20 * 45, 20 * 120, 20 * 30, 20 * 60,
                PersistentEffects.malaiseOnly()
            ),
            new ViralContagion(8.0, 0.50f, 0.15, 12, 0.20f, 0.85f, 0.90f, 24000, 24000, 0.0f, 0.0f, false, false),
            new AcquisitionRule(false, false, false, 0.0, 0.0),
            "message.simplediseases.caught_norovirus", "message.simplediseases.cured_norovirus",
            0.1, 0.5
        ));

        // Pneumonia: viral complication (flu/cold/rsv source), 4 tiers, stochastic momentum worsening.
        register(new ComplicationDiseaseDef(
            PNEUMONIA, 4, ConditionType.RESPIRATORY, GROUP_VIRAL, 10.0, 1.0, 20L * 60 * 15, 20L * 60 * 30,
            symptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,
                            () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), SymptomBand.COMMON, 200),
                    new SymptomEntry(DiseaseEffects.BLOODY_COUGHING, SymptomAction.DAMAGE,
                            () -> DiseaseSounds.COUGH.get(), SymptomBand.COMMON, 100)
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,       SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.VOMITING,    SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get()),
                    new SymptomEntry(DiseaseEffects.PRODUCTIVE_COUGHING, SymptomAction.NONE, () -> DiseaseSounds.COUGH.get())
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.TACHYPNEA, SymptomAction.NONE,
                            () -> DiseaseSounds.RAPID_BREATHING.get(), SymptomBand.ADVANCED),
                    new SymptomEntry(DiseaseEffects.TACHYCARDIA, SymptomAction.NONE,
                            () -> DiseaseSounds.HEARTBEAT.get(), SymptomBand.ADVANCED),
                    new SymptomEntry(DiseaseEffects.CONFUSION, SymptomAction.NONE, SymptomBand.ADVANCED)
                ),
                20 * 30, 20 * 90, 20 * 30, 20 * 60,
                PersistentEffects.withPain(PainProfile.PNEUMONIA)
            ),
            "message.simplediseases.caught_pneumonia", "message.simplediseases.cured_pneumonia",
            // Viral gate, stochastic momentum worsening
            Optional.empty(),                    // triggeredBy
            1.0 / 12000.0,                       // decayRate (pre-latch fade when source drops)
            Optional.empty(),                    // accumulationRate (computed from latchTicks)
            Optional.empty(),                    // passiveRecoveryRate (inherit source viral rate)
            0.0,                                 // worseningRate=0 → stochastic momentum
            List.of(3.0, 6.0, 8.0)              // worseningThresholds (4-tier, cap 10)
        ));

        // Bronchitis: viral complication (flu/cold/rsv source), 3 tiers, stochastic momentum worsening.
        register(new ComplicationDiseaseDef(
            BRONCHITIS, 3, ConditionType.RESPIRATORY, GROUP_VIRAL, 10.0, 1.0, 20L * 60 * 15, 20L * 60 * 30,
            symptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,
                            () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), SymptomBand.COMMON, 200)
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,       SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    new SymptomEntry(DiseaseEffects.WHEEZING,    SymptomAction.NONE, () -> DiseaseSounds.WHEEZING_SOUND.get(),
                            SymptomBand.COMMON, 0, true),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.VOMITING,    SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get()),
                    new SymptomEntry(DiseaseEffects.PRODUCTIVE_COUGHING, SymptomAction.NONE, () -> DiseaseSounds.COUGH.get())
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.TACHYPNEA, SymptomAction.NONE,
                            () -> DiseaseSounds.RAPID_BREATHING.get(), SymptomBand.ADVANCED)
                ),
                20 * 30, 20 * 90, 20 * 30, 20 * 60,
                PersistentEffects.withPain(PainProfile.MILD_FLAT)
            ),
            "message.simplediseases.caught_bronchitis", "message.simplediseases.cured_bronchitis",
            // Viral gate, stochastic momentum worsening
            Optional.empty(),                    // triggeredBy
            1.0 / 12000.0,                       // decayRate
            Optional.empty(),                    // accumulationRate (from latchTicks)
            Optional.empty(),                    // passiveRecoveryRate (inherit source viral rate)
            0.0,                                 // worseningRate=0 → stochastic momentum
            List.of(4.0, 7.0)                   // worseningThresholds (3-tier, cap 10)
        ));

        // Staph cellulitis: wound-seeded bacterial infection.
        register(new BacterialDiseaseDef(
            CELLULITIS_STAPH, 3, ConditionType.TISSUE, GROUP_BACTERIAL, 2.0, 1.0,
            1.0 / 4800.0,
            1.0 / 12000.0,
            1.0 / 9000.0,
            List.of(1.5, 2.0),
            symptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.LOCALIZED_REDNESS, SymptomAction.NONE, SymptomTiming.STATIC)
                ),
                List.of(),
                List.of(
                    new SymptomEntry(DiseaseEffects.HYPOTENSION, SymptomAction.HYPOTENSION, SymptomBand.ADVANCED, 200),
                    new SymptomEntry(DiseaseEffects.TACHYCARDIA, SymptomAction.NONE,
                            () -> DiseaseSounds.HEARTBEAT.get(), SymptomBand.ADVANCED),
                    new SymptomEntry(DiseaseEffects.CONFUSION, SymptomAction.NONE, SymptomBand.ADVANCED)
                ),
                20 * 60, 20 * 180, 20 * 30, 20 * 90,
                PersistentEffects.withPain(PainProfile.CELLULITIS)
            ),
            "message.simplediseases.caught_cellulitis",
            "message.simplediseases.cured_cellulitis",
            "message.simplediseases.cellulitis_worsens"
        ));

        // Sepsis (staph): bacterial complication triggered by severe cellulitis at cap. 4 tiers; deterministic
        // worsening drives toward Debilitating, which gates MOF.
        register(new ComplicationDiseaseDef(
            SEPSIS_STAPH, 4, ConditionType.SYSTEMIC, GROUP_BACTERIAL, 10.0, 1.0, 0L, 0L,
            symptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.HYPOTENSION, SymptomAction.HYPOTENSION, SymptomBand.COMMON, 200)
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.LOCALIZED_REDNESS, SymptomAction.NONE,
                            SymptomBand.COMMON, SymptomTiming.STATIC, true),
                    new SymptomEntry(DiseaseEffects.CONFUSION, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.TACHYCARDIA, SymptomAction.NONE,
                            () -> DiseaseSounds.HEARTBEAT.get()),
                    new SymptomEntry(DiseaseEffects.TACHYPNEA, SymptomAction.NONE,
                            () -> DiseaseSounds.RAPID_BREATHING.get())
                ),
                List.of(
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,
                            () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), SymptomBand.ADVANCED, 200, true),
                    new SymptomEntry(DiseaseEffects.MOTTLED_SKIN, SymptomAction.NONE,
                            SymptomBand.ADVANCED, SymptomTiming.STATIC)
                ),
                List.of(new SymptomSupersedes(DiseaseEffects.LOCALIZED_REDNESS, DiseaseEffects.MOTTLED_SKIN)),
                20 * 90, 20 * 240, 20 * 45, 20 * 120,
                PersistentEffects.withPain(PainProfile.SEPSIS)
            ),
            "message.simplediseases.caught_sepsis",
            "message.simplediseases.cured_sepsis",
            Optional.of("cellulitis_staph"),     // triggeredBy
            1.0 / 24000.0,                       // decayRate
            Optional.of(1.0 / 12000.0),          // accumulationRate (~10-20 min to latch)
            Optional.empty(),                    // passiveRecoveryRate (no recovery)
            1.0 / 9000.0,                        // worseningRate > 0 → deterministic
            List.of(2.5, 5.0, 7.5)              // 3 thresholds for 4 tiers; Debilitating at 7.5
        ));

        // Multiple Organ Failure: complication of Debilitating sepsis. No symptoms; once latched it
        // applies direct lethal damage at Wither-I rate until the player dies or is cured.
        register(new ComplicationDiseaseDef(
            MOF_STAPH, 1, ConditionType.SYSTEMIC, GROUP_BACTERIAL, 10.0, 1.0, 0L, 0L,
            SymptomConfig.empty(),
            "message.simplediseases.caught_mof",
            "message.simplediseases.cured_mof",
            Optional.of("sepsis_staph"),         // triggeredBy
            1.0 / 12000.0,                       // decayRate (slow decay when gate closes pre-latch)
            Optional.of(1.0 / 6000.0),           // accumulationRate (~5 min to latch from gate open)
            Optional.empty(),                    // passiveRecoveryRate (no recovery)
            0.0,                                 // no worsening (single tier)
            List.of()
        ));
    }

    public static DiseaseDef register(DiseaseDef def) {
        BY_ID.put(def.id(), def);
        rebuildCaches();
        return def;
    }

    private static void rebuildCaches() {
        allList         = List.copyOf(BY_ID.values());
        viralList       = BY_ID.values().stream()
            .filter(d -> d.category() == com.theblackbaron.simplediseases.status.category.DiseaseCategories.VIRAL)
            .toList();
        contagiousList  = BY_ID.values().stream().filter(d -> d.category().contagious()).toList();
        environmentalList = viralList.stream()
            .filter(d -> !((ViralDiseaseDef) d).acquisition().isInert()).toList();
        complicationList = BY_ID.values().stream()
            .filter(d -> d.category() == com.theblackbaron.simplediseases.status.category.DiseaseCategories.COMPLICATION)
            .toList();
        bacterialList = BY_ID.values().stream()
            .filter(d -> d.category() == com.theblackbaron.simplediseases.status.category.DiseaseCategories.BACTERIAL)
            .toList();
    }

    public static DiseaseDef get(ResourceLocation id) { return BY_ID.get(id); }
    public static Collection<DiseaseDef> all()          { return allList; }
    public static Collection<DiseaseDef> contagious()   { return contagiousList; }
    public static Collection<DiseaseDef> viral()        { return viralList; }
    public static Collection<DiseaseDef> environmental(){ return environmentalList; }
    public static Collection<DiseaseDef> complications(){ return complicationList; }
    public static Collection<DiseaseDef> bacterial()    { return bacterialList; }
}
