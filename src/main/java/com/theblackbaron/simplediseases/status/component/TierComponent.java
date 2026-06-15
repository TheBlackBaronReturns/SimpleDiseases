package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theblackbaron.simplediseases.status.def.Severity;

/**
 * Per-illness severity state. {@code severity} is the rolled {@link Severity} ordinal (0–4), assigned
 * once progress reaches the first symptom threshold; {@code -1} means not yet rolled.
 * {@code reductions} counts successful treatment-driven tier reductions, which decay the chance of
 * further reductions. {@code worseningChecks} is a bitmask of progress thresholds already evaluated
 * for this illness, {@code worsenings} counts successful threshold-driven tier increases, and
 * {@code previousWorseningProgress} lets threshold rolls require a real upward crossing. All reset on
 * cure/death.
 */
public final class TierComponent implements DiseaseComponent {
    public int    severity                  = -1;
    public int    reductions                = 0;
    public int    worseningChecks           = 0;
    public int    worsenings                = 0;
    public double previousWorseningProgress = 0.0;

    public TierComponent() {}

    public TierComponent(int severity, int reductions) {
        this(severity, reductions, 0, 0, 0.0);
    }

    public TierComponent(int severity, int reductions, int worseningChecks, int worsenings) {
        this(severity, reductions, worseningChecks, worsenings, 0.0);
    }

    public TierComponent(int severity, int reductions, int worseningChecks, int worsenings, double previousWorseningProgress) {
        this.severity                  = severity;
        this.reductions                = reductions;
        this.worseningChecks           = worseningChecks;
        this.worsenings                = worsenings;
        this.previousWorseningProgress = previousWorseningProgress;
    }

    public boolean  rolled()    { return severity >= 0; }
    public Severity severity()  { return Severity.byOrdinal(severity); }

    public static final Codec<TierComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.optionalFieldOf("severity", -1).forGetter(c -> c.severity),
        Codec.INT.optionalFieldOf("reductions", 0).forGetter(c -> c.reductions),
        Codec.INT.optionalFieldOf("worsening_checks", 0).forGetter(c -> c.worseningChecks),
        Codec.INT.optionalFieldOf("worsenings", 0).forGetter(c -> c.worsenings),
        Codec.DOUBLE.optionalFieldOf("previous_worsening_progress", 0.0).forGetter(c -> c.previousWorseningProgress)
    ).apply(i, TierComponent::new));
}
