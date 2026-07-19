package com.theblackbaron.simplediseases.status;

/**
 * Reserved low {@link net.minecraftforge.common.extensions.IForgeMobEffect#getSortOrder} bands so
 * Simple Diseases icons sort left of vanilla/mod defaults (which tie-break on potion color ~0x500000+).
 * Final order still respects {@code MobEffectInstance} duration/ambient rules first.
 */
public final class EffectHudSort {
    private EffectHudSort() {}

    public static final int IMMUNITY_BASE = 0;
    public static final int DISEASE_BASE  = 1_000;
    public static final int SYMPTOM_BASE  = 2_000;
    public static final int SD_OTHER_BASE = 3_000;

    // --- Immunity (leftmost) ------------------------------------------------------------------------
    public static final int IMMUNE             = immunity(0);
    public static final int IMMUNE_DEFICIENCY  = immunity(1);

    // --- Disease paths (tier variants share one slot) -----------------------------------------------
    public static final int DISEASE_COLD              = disease(0);
    public static final int DISEASE_FLU               = disease(1);
    public static final int DISEASE_RSV               = disease(2);
    public static final int DISEASE_NOROVIRUS         = disease(3);
    public static final int DISEASE_CELLULITIS        = disease(4);
    public static final int DISEASE_PNEUMONIA_FLU     = disease(5);
    public static final int DISEASE_PNEUMONIA_COLD    = disease(6);
    public static final int DISEASE_PNEUMONIA_RSV     = disease(7);
    public static final int DISEASE_BRONCHITIS_FLU    = disease(8);
    public static final int DISEASE_BRONCHITIS_COLD   = disease(9);
    public static final int DISEASE_BRONCHITIS_RSV    = disease(10);
    public static final int DISEASE_SEPSIS            = disease(11);
    public static final int DISEASE_MOF               = disease(12);
    public static final int DISEASE_FLESH_WOUND       = disease(13);

    // --- Symptoms ---------------------------------------------------------------------------------
    public static final int SYMPTOM_COUGH               = symptom(0);
    public static final int SYMPTOM_SNEEZING            = symptom(1);
    public static final int SYMPTOM_MALAISE             = symptom(2);
    public static final int SYMPTOM_VOMITING            = symptom(3);
    public static final int SYMPTOM_SHORTNESS_OF_BREATH = symptom(4);
    public static final int SYMPTOM_HYPOTENSION         = symptom(5);
    public static final int SYMPTOM_HEADACHE            = symptom(6);
    public static final int SYMPTOM_SORE_THROAT         = symptom(7);
    public static final int SYMPTOM_STOMACH_CRAMPS      = symptom(8);
    public static final int SYMPTOM_DIARRHEA            = symptom(9);
    public static final int SYMPTOM_MOTTLED_SKIN        = symptom(10);
    public static final int SYMPTOM_BLOODY_COUGHING     = symptom(11);
    public static final int SYMPTOM_TACHYCARDIA         = symptom(12);
    public static final int SYMPTOM_TACHYPNEA           = symptom(13);
    public static final int SYMPTOM_WHEEZING            = symptom(14);
    public static final int SYMPTOM_CONFUSION           = symptom(15);
    public static final int SYMPTOM_PRODUCTIVE_COUGHING = symptom(16);
    public static final int SYMPTOM_LOCALIZED_REDNESS   = symptom(17);
    public static final int SYMPTOM_PAIN                = symptom(18);

    // --- Injury / indicators / treatment ----------------------------------------------------------
    public static final int SD_DAMP              = sdOther(0);
    public static final int SD_CHILLY_WIND       = sdOther(1);
    public static final int SD_SYMPTOMS_MANAGED  = sdOther(5);
    public static final int SD_TREATMENT_APPLIED = sdOther(6);

    public static int immunity(int slot) { return IMMUNITY_BASE + slot; }
    public static int disease(int slot)  { return DISEASE_BASE + slot; }
    public static int symptom(int slot)  { return SYMPTOM_BASE + slot; }
    public static int sdOther(int slot)  { return SD_OTHER_BASE + slot; }

    /** Maps a disease registry path to its HUD sort slot. */
    public static int diseaseForPath(String path) {
        return switch (path) {
            case "cold"              -> DISEASE_COLD;
            case "flu"               -> DISEASE_FLU;
            case "rsv"               -> DISEASE_RSV;
            case "norovirus"         -> DISEASE_NOROVIRUS;
            case "cellulitis_staph"  -> DISEASE_CELLULITIS;
            case "pneumonia_flu"     -> DISEASE_PNEUMONIA_FLU;
            case "pneumonia_cold"    -> DISEASE_PNEUMONIA_COLD;
            case "pneumonia_rsv"     -> DISEASE_PNEUMONIA_RSV;
            case "bronchitis_flu"    -> DISEASE_BRONCHITIS_FLU;
            case "bronchitis_cold"   -> DISEASE_BRONCHITIS_COLD;
            case "bronchitis_rsv"    -> DISEASE_BRONCHITIS_RSV;
            case "sepsis_staph"      -> DISEASE_SEPSIS;
            case "mof", "mof_staph"  -> DISEASE_MOF;
            default                  -> DISEASE_BASE + 90;
        };
    }
}
