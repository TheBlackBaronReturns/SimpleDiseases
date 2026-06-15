package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * State for the shared symptom-episode service: the selected-symptom bitmask (bit index = position
 * in the disease's symptom list) and the game-time tick of the next scheduled recovery episode
 * (0 = unscheduled). The bitmask grows by high-water mark during accumulation and is frozen during
 * recovery — see the symptom service.
 */
public final class SymptomPoolComponent implements DiseaseComponent {
    public int  mask;
    public long nextEpisodeAt;

    public SymptomPoolComponent() {}

    public SymptomPoolComponent(int mask, long nextEpisodeAt) {
        this.mask          = mask;
        this.nextEpisodeAt = nextEpisodeAt;
    }

    public boolean has(int bit)   { return (mask & (1 << bit)) != 0; }
    public void    set(int bit)   { mask |=  (1 << bit); }
    public void    clear(int bit) { mask &= ~(1 << bit); }
    public void    clearAll()     { mask = 0; }
    public int     count()        { return Integer.bitCount(mask); }

    public static final Codec<SymptomPoolComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("mask").forGetter(c -> c.mask),
        Codec.LONG.fieldOf("nextEpisodeAt").forGetter(c -> c.nextEpisodeAt)
    ).apply(i, SymptomPoolComponent::new));
}
