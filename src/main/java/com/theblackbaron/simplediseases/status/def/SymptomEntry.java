package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * One symptom in a disease's pool: the MobEffect it manifests as, an optional side effect, an optional
 * onset sound (played when the symptom episode begins — only "certain symptoms" carry one), and the
 * minimum rolled {@link Severity} at which it is eligible to enter the pool. The entry's index in the
 * pool list is its bit position in {@link
 * com.theblackbaron.simplediseases.status.component.SymptomPoolComponent}.
 *
 * <p>{@code minSeverity} gates membership only: a symptom is eligible once the illness's rolled tier is
 * at or above it, but selection is still random, so e.g. vomiting (min SEVERE) is <i>possible</i> in a
 * severe/debilitating flu, not guaranteed. The default {@link Severity#LIGHT} means "always eligible".
 *
 * <p>{@code amplifier} sets a fixed MobEffect amplifier (0-based). Ignored when {@code severityAmp}
 * is true (which instead scales the amplifier by illness tier). Useful for symptoms that should always
 * fire at a specific tier (e.g. sharp pain at amp 2 = tier 3 for sepsis).
 */
public record SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Optional<Supplier<SoundEvent>> sound,
                           Severity minSeverity, Optional<Integer> durationTicks, boolean severityAmp, int amplifier) {

    /** No side effect, no sound, always eligible, config-paced. */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action) {
        this(effect, action, Optional.empty(), Severity.LIGHT, Optional.empty(), false, 0);
    }

    /** No sound, always eligible, with a fixed side-effect duration (e.g. headache's ~10 s nausea burst). */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, int durationTicks) {
        this(effect, action, Optional.empty(), Severity.LIGHT, Optional.of(durationTicks), false, 0);
    }

    /** With an onset sound, always eligible, config-paced. */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound) {
        this(effect, action, Optional.of(sound), Severity.LIGHT, Optional.empty(), false, 0);
    }

    /** No sound, config-paced, with a minimum-severity gate (e.g. dehydration on moderate+ illness). */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Severity minSeverity) {
        this(effect, action, Optional.empty(), minSeverity, Optional.empty(), false, 0);
    }

    /** With an onset sound and a minimum-severity gate, config-paced. */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound, Severity minSeverity) {
        this(effect, action, Optional.of(sound), minSeverity, Optional.empty(), false, 0);
    }

    /** With an onset sound, a min-severity gate, and a fixed side-effect duration. For symptoms whose
     *  marker icon spans the full episode but whose impact is a brief burst (e.g. shortness of breath). */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, Supplier<SoundEvent> sound, Severity minSeverity, int durationTicks) {
        this(effect, action, Optional.of(sound), minSeverity, Optional.of(durationTicks), false, 0);
    }

    /** No sound, always eligible, config-paced, with tier-scaled amplifier. Effect amp = severity - MILD (clamped ≥ 0). */
    public SymptomEntry(Supplier<MobEffect> effect, SymptomAction action, boolean severityAmp) {
        this(effect, action, Optional.empty(), Severity.LIGHT, Optional.empty(), severityAmp, 0);
    }

    public static final Codec<SymptomEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
        DiseaseCodecs.EFFECT.fieldOf("effect").forGetter(SymptomEntry::effect),
        SymptomAction.CODEC.optionalFieldOf("action", SymptomAction.NONE).forGetter(SymptomEntry::action),
        DiseaseCodecs.SOUND.optionalFieldOf("sound").forGetter(SymptomEntry::sound),
        Severity.CODEC.optionalFieldOf("min_severity", Severity.LIGHT).forGetter(SymptomEntry::minSeverity),
        Codec.INT.optionalFieldOf("duration_ticks").forGetter(SymptomEntry::durationTicks),
        Codec.BOOL.optionalFieldOf("severity_amp", false).forGetter(SymptomEntry::severityAmp),
        Codec.INT.optionalFieldOf("amplifier", 0).forGetter(SymptomEntry::amplifier)
    ).apply(i, SymptomEntry::new));
}
