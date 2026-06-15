package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Scalar accumulation progress plus the latched "in recovery" flag. Used by accumulation-and-latch
 * categories (viral, and bacterial's pre-incubation accumulation). The cap and latch threshold are
 * static tuning on the disease definition, not stored here — this holds only the live values.
 */
public final class ProgressComponent implements DiseaseComponent {
    public double  progress;
    public boolean inRecovery;

    public ProgressComponent() {}

    public ProgressComponent(double progress, boolean inRecovery) {
        this.progress   = progress;
        this.inRecovery = inRecovery;
    }

    /** Clamp-adds {@code delta} into [0, cap]; ignores NaN. Mirrors the old DiseaseData.add*Progress. */
    public void add(double delta, double cap) {
        if (Double.isNaN(delta)) return;
        progress = Math.max(0.0, Math.min(cap, progress + delta));
    }

    public static final Codec<ProgressComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.DOUBLE.fieldOf("progress").forGetter(c -> c.progress),
        Codec.BOOL.fieldOf("inRecovery").forGetter(c -> c.inRecovery)
    ).apply(i, ProgressComponent::new));
}
