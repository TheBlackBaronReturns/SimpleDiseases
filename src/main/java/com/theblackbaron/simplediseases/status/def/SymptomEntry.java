package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * One symptom in a disease config. Bit index in {@link SymptomConfig#allEntries()} maps to
 * {@link com.theblackbaron.simplediseases.status.component.SymptomPoolComponent} mask bits.
 */
public record SymptomEntry(
    Supplier<MobEffect> effect,
    SymptomAction action,
    Optional<Supplier<SoundEvent>> sound,
    SymptomBand band,
    SymptomTiming timing,
    Optional<Integer> durationTicks,
    int amplifier,
    boolean inheritOnly
) {
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action) {
        this(effect, action, Optional.empty(), SymptomBand.COMMON, SymptomTiming.EPISODIC, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, int durationTicks) {
        this(effect, action, Optional.empty(), SymptomBand.COMMON, SymptomTiming.EPISODIC, Optional.of(durationTicks), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound) {
        this(effect, action, Optional.of(sound), SymptomBand.COMMON, SymptomTiming.EPISODIC, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, SymptomBand band) {
        this(effect, action, Optional.empty(), band, SymptomTiming.EPISODIC, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, SymptomBand band, int durationTicks) {
        this(effect, action, Optional.empty(), band, SymptomTiming.EPISODIC, Optional.of(durationTicks), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound, SymptomBand band) {
        this(effect, action, Optional.of(sound), band, SymptomTiming.EPISODIC, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound,
                          SymptomBand band, int durationTicks) {
        this(effect, action, Optional.of(sound), band, SymptomTiming.EPISODIC, Optional.of(durationTicks), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, SymptomTiming timing) {
        this(effect, action, Optional.empty(), SymptomBand.COMMON, timing, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, SymptomBand band, SymptomTiming timing) {
        this(effect, action, Optional.empty(), band, timing, Optional.empty(), 0, false);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, SymptomBand band,
                          SymptomTiming timing, boolean inheritOnly) {
        this(effect, action, Optional.empty(), band, timing, Optional.empty(), 0, inheritOnly);
    }

    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound,
                          SymptomBand band, int durationTicks, boolean inheritOnly) {
        this(effect, action, Optional.of(sound), band, SymptomTiming.EPISODIC, Optional.of(durationTicks), 0, inheritOnly);
    }

    public static final Codec<SymptomEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
        DiseaseCodecs.EFFECT.fieldOf("effect").forGetter(SymptomEntry::effect),
        SymptomAction.CODEC.optionalFieldOf("action", SymptomAction.NONE).forGetter(SymptomEntry::action),
        DiseaseCodecs.SOUND.optionalFieldOf("sound").forGetter(SymptomEntry::sound),
        SymptomBand.CODEC.optionalFieldOf("band", SymptomBand.COMMON).forGetter(SymptomEntry::band),
        SymptomTiming.CODEC.optionalFieldOf("timing", SymptomTiming.EPISODIC).forGetter(SymptomEntry::timing),
        Codec.INT.optionalFieldOf("duration_ticks").forGetter(SymptomEntry::durationTicks),
        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(SymptomEntry::amplifier),
        Codec.BOOL.optionalFieldOf("inherit_only", false).forGetter(SymptomEntry::inheritOnly)
    ).apply(i, SymptomEntry::new));
}
