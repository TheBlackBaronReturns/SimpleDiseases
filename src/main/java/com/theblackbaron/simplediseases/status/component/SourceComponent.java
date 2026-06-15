package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/**
 * State for a "viral complication" (pneumonia): which source viral disease caused it, the per-instance
 * randomized tick count of qualifying illness needed to latch (the 15–30 min onset is randomized per
 * case), and the pool index of the one random source symptom this case manifests besides the locked
 * Bad Cough + Shortness of Breath. {@code null}/{@code -1} mean unset (no source recorded yet).
 */
public final class SourceComponent implements DiseaseComponent {
    public ResourceLocation sourceId   = null;
    public long             latchTicks = 0L;
    public int              symptomBit = -1;

    public SourceComponent() {}

    public SourceComponent(String sourceId, long latchTicks, int symptomBit) {
        this.sourceId   = sourceId.isEmpty() ? null : new ResourceLocation(sourceId);
        this.latchTicks = latchTicks;
        this.symptomBit = symptomBit;
    }

    public boolean hasSource() { return sourceId != null; }

    public void clear() { sourceId = null; latchTicks = 0L; symptomBit = -1; }

    public static final Codec<SourceComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.STRING.optionalFieldOf("source", "").forGetter(c -> c.sourceId == null ? "" : c.sourceId.toString()),
        Codec.LONG.optionalFieldOf("latch_ticks", 0L).forGetter(c -> c.latchTicks),
        Codec.INT.optionalFieldOf("symptom_bit", -1).forGetter(c -> c.symptomBit)
    ).apply(i, SourceComponent::new));
}
