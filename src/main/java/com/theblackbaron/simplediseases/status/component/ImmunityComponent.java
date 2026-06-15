package com.theblackbaron.simplediseases.status.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Post-recovery immunity window: game-time tick at which immunity to this disease expires (0 = none). */
public final class ImmunityComponent implements DiseaseComponent {
    public long immunityUntil;

    public ImmunityComponent() {}

    public ImmunityComponent(long immunityUntil) {
        this.immunityUntil = immunityUntil;
    }

    public static final Codec<ImmunityComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.LONG.fieldOf("immunityUntil").forGetter(c -> c.immunityUntil)
    ).apply(i, ImmunityComponent::new));
}
