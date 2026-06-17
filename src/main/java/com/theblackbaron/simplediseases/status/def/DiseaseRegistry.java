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

    public static final String GROUP_VIRAL    = "viral";
    public static final String GROUP_BACTERIAL = "bacterial";

    public static final long VIRAL_IMMUNITY_TICKS = 20L * 60 * 20; // 20 minutes

    private static final Map<ResourceLocation, DiseaseDef> BY_ID = new LinkedHashMap<>();

    private static List<DiseaseDef> allList           = List.of();
    private static List<DiseaseDef> viralList         = List.of();
    private static List<DiseaseDef> contagiousList    = List.of();
    private static List<DiseaseDef> environmentalList = List.of();
    private static List<DiseaseDef> complicationList  = List.of();
    private static List<DiseaseDef> bacterialList     = List.of();

    public static void bootstrap() {
        if (!BY_ID.isEmpty()) return;

        register(new ViralDiseaseDef(
            COLD, 3, () -> DiseaseParticles.COLD.get(), GROUP_VIRAL,
            2.0, 1.0, 0.000030, 24000L,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,    SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING, SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE)
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 120, 20 * 300, 20 * 30, 20 * 60
            ),
            new ViralContagion(6.0, 0.167f, 0.05, 115, 0.10f, 0.25f, 0.35f, 24000, 24000, 0.05f, 0.0f, true, true),
            AcquisitionRule.defaultDisease(),
            "message.simplediseases.caught_cold", "message.simplediseases.cured_cold",
            0.1, 0.5
        ));

        register(new ViralDiseaseDef(
            FLU, 4, () -> DiseaseParticles.FLU.get(), GROUP_VIRAL,
            10.0, 1.0, 0.000030, 48000L,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,       SymptomAction.NONE,       () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE,       () -> DiseaseSounds.SNEEZE.get()),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.VOMITING,    SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get(), Severity.SEVERE),
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS, () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), Severity.SEVERE, 200)
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 60, 20 * 180, 20 * 45, 20 * 90
            ),
            new ViralContagion(6.0, 0.334f, 0.10, 20, 0.20f, 0.70f, 0.80f, 24000, 24000, 0.0f, 0.0f, true, true),
            new AcquisitionRule(false, true, false, 0.6, 0.0),
            "message.simplediseases.caught_flu", "message.simplediseases.cured_flu",
            0.5, 1.0
        ));

        register(new ViralDiseaseDef(
            RSV, 3, () -> DiseaseParticles.RSV.get(), GROUP_VIRAL,
            10.0, 1.0, 0.000030, 36000L,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.COUGH,    SymptomAction.NONE, () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING, SymptomAction.NONE, () -> DiseaseSounds.SNEEZE.get()),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE)
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 90, 20 * 210, 20 * 40, 20 * 80
            ),
            new ViralContagion(6.0, 0.334f, 0.10, 20, 0.20f, 0.70f, 0.80f, 24000, 24000, 0.0f, 0.15f, true, true),
            new AcquisitionRule(false, false, true, 0.20, 0.40),
            "message.simplediseases.caught_rsv", "message.simplediseases.cured_rsv",
            0.1, 0.5
        ));

        register(new ViralDiseaseDef(
            NOROVIRUS, 3, () -> DiseaseParticles.NOROVIRUS.get(), GROUP_VIRAL,
            2.0, 1.0, 0.00006, 6000L,
            new SymptomConfig(
                List.of(
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.HEADACHE,       SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.VOMITING,       SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get()),
                    new SymptomEntry(DiseaseEffects.DIARRHEA,       SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.DIARRHEA.get()),
                    new SymptomEntry(DiseaseEffects.STOMACH_CRAMPS, SymptomAction.NONE, () -> DiseaseSounds.STOMACH_CRAMPS.get())
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 45, 20 * 120, 20 * 30, 20 * 60
            ),
            new ViralContagion(8.0, 0.50f, 0.15, 12, 0.20f, 0.85f, 0.90f, 24000, 24000, 0.0f, 0.0f, false, false),
            new AcquisitionRule(false, false, false, 0.0, 0.0),
            "message.simplediseases.caught_norovirus", "message.simplediseases.cured_norovirus",
            0.1, 0.5
        ));

        // Pneumonia: viral complication (flu/cold/rsv source), 4 tiers, stochastic momentum worsening.
        register(new ComplicationDiseaseDef(
            PNEUMONIA, 4, GROUP_VIRAL, 10.0, 1.0, 20L * 60 * 15, 20L * 60 * 30,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.BAD_COUGH,           SymptomAction.DAMAGE,     () -> DiseaseSounds.COUGH.get(), Severity.LIGHT, 100),
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS,  () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), Severity.LIGHT, 200),
                    new SymptomEntry(DiseaseEffects.COUGH,       SymptomAction.NONE,       () -> DiseaseSounds.COUGH.get()),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE,       () -> DiseaseSounds.SNEEZE.get()),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.VOMITING,    SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get())
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 30, 20 * 90, 20 * 30, 20 * 60
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
            BRONCHITIS, 3, GROUP_VIRAL, 10.0, 1.0, 20L * 60 * 15, 20L * 60 * 30,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.SHORTNESS_OF_BREATH, SymptomAction.BREATHLESS, () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), Severity.LIGHT, 200),
                    new SymptomEntry(DiseaseEffects.COUGH,               SymptomAction.NONE,       () -> DiseaseSounds.COUGH.get(), Severity.LIGHT),
                    new SymptomEntry(DiseaseEffects.SNEEZING,    SymptomAction.NONE,       () -> DiseaseSounds.SNEEZE.get()),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.HEADACHE,    SymptomAction.NAUSEA, 200),
                    new SymptomEntry(DiseaseEffects.SORE_THROAT, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.VOMITING,    SymptomAction.DRAIN_FOOD, () -> DiseaseSounds.VOMIT.get())
                ),
                List.of(0.10, 0.40, 0.70),
                20 * 30, 20 * 90, 20 * 30, 20 * 60
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
            CELLULITIS_STAPH, 3, GROUP_BACTERIAL, 2.0, 1.0,
            1.0 / 4800.0,
            1.0 / 12000.0,
            1.0 / 9000.0,
            List.of(4.0 / 3.0, 5.0 / 3.0),
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.SHARP_PAIN, SymptomAction.NONE,
                            Optional.empty(), Severity.LIGHT, Optional.empty(), false, 1, false),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.LOCALIZED_REDNESS, SymptomAction.NONE)
                ),
                List.of(0.1, 0.4, 0.7),
                20 * 60, 20 * 180, 20 * 30, 20 * 90
            ),
            "message.simplediseases.caught_cellulitis",
            "message.simplediseases.cured_cellulitis",
            "message.simplediseases.cellulitis_worsens"
        ));

        // Sepsis (staph): bacterial complication triggered by severe cellulitis at cap. 4 tiers; deterministic
        // worsening drives toward Debilitating, which gates MOF.
        register(new ComplicationDiseaseDef(
            SEPSIS_STAPH, 4, GROUP_BACTERIAL, 10.0, 1.0, 0L, 0L,
            new SymptomConfig(
                List.of(
                    new SymptomEntry(DiseaseEffects.HYPOTENSION,    SymptomAction.BREATHLESS,  () -> DiseaseSounds.SHORTNESS_OF_BREATH.get(), Severity.LIGHT, 200),
                    new SymptomEntry(DiseaseEffects.SHARP_PAIN,     SymptomAction.NONE,        Optional.empty(), Severity.LIGHT, Optional.empty(), false, 2, false),
                    SymptomEntry.withFeverAmp(DiseaseEffects.MALAISE, SymptomAction.NONE),
                    new SymptomEntry(DiseaseEffects.MOTTLED_SKIN,   SymptomAction.NONE,        Severity.MODERATE)
                ),
                List.of(0.1, 0.4, 0.7),
                20 * 90, 20 * 240, 20 * 45, 20 * 120
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
            MOF_STAPH, 1, GROUP_BACTERIAL, 10.0, 1.0, 0L, 0L,
            new SymptomConfig(List.of(), List.of(), 0, 0, 0, 0),
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
