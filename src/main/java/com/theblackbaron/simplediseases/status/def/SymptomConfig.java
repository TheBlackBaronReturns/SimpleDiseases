package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.world.effect.MobEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Layered symptom configuration: hallmarks (fixed order), common pool draws, severe (Advanced band),
 * plus pacing thresholds and persistent malaise/pain handled outside the pool.
 */
public record SymptomConfig(
    List<SymptomEntry> hallmarks,
    List<SymptomEntry> commonAdds,
    List<SymptomEntry> severe,
    List<SymptomSupersedes> exclusivePairs,
    List<Double> thresholds,
    int minIntervalTicks,
    int maxIntervalTicks,
    int minDurationTicks,
    int maxDurationTicks,
    PersistentEffects persistentEffects,
    List<SymptomEntry> allEntries
) {
    public SymptomConfig(
        List<SymptomEntry> hallmarks,
        List<SymptomEntry> commonAdds,
        List<SymptomEntry> severe,
        List<SymptomSupersedes> exclusivePairs,
        List<Double> thresholds,
        int minIntervalTicks,
        int maxIntervalTicks,
        int minDurationTicks,
        int maxDurationTicks,
        PersistentEffects persistentEffects
    ) {
        this(
            List.copyOf(hallmarks),
            List.copyOf(commonAdds),
            List.copyOf(severe),
            exclusivePairs == null ? List.of() : List.copyOf(exclusivePairs),
            List.copyOf(thresholds),
            minIntervalTicks,
            maxIntervalTicks,
            minDurationTicks,
            maxDurationTicks,
            persistentEffects == null ? PersistentEffects.NONE : persistentEffects,
            buildAll(hallmarks, commonAdds, severe)
        );
    }

    public static SymptomConfig empty() {
        return new SymptomConfig(List.of(), List.of(), List.of(), List.of(), List.of(),
                0, 0, 0, 0, PersistentEffects.NONE);
    }

    private static List<SymptomEntry> buildAll(List<SymptomEntry> hallmarks,
                                                List<SymptomEntry> commonAdds,
                                                List<SymptomEntry> severe) {
        List<SymptomEntry> all = new ArrayList<>(hallmarks.size() + commonAdds.size() + severe.size());
        all.addAll(hallmarks);
        all.addAll(commonAdds);
        all.addAll(severe);
        return List.copyOf(all);
    }

    public int symptomBits() { return allEntries.size(); }

    public SymptomEntry entryAt(int bit) { return allEntries.get(bit); }

    public int hallmarkCount() { return hallmarks.size(); }

    public boolean isHallmark(int bit) { return bit < hallmarks.size(); }

    public boolean isCommon(int bit) {
        return bit >= hallmarks.size() && bit < hallmarks.size() + commonAdds.size();
    }

    public boolean isSevere(int bit) { return bit >= hallmarks.size() + commonAdds.size(); }

    /** @deprecated Use {@link #allEntries()}. */
    @Deprecated
    public List<SymptomEntry> pool() { return allEntries; }

    public int indexOfEffect(MobEffect effect) {
        for (int i = 0; i < allEntries.size(); i++) {
            if (allEntries.get(i).effect().get() == effect) return i;
        }
        return -1;
    }

    /** Bidirectional pool exclusion partner for {@code effect}, if configured in {@link #exclusivePairs}. */
    public Optional<MobEffect> exclusivePeer(MobEffect effect) {
        for (SymptomSupersedes pair : exclusivePairs) {
            MobEffect a = pair.common().get();
            MobEffect b = pair.advanced().get();
            if (a == effect) return Optional.of(b);
            if (b == effect) return Optional.of(a);
        }
        return Optional.empty();
    }

    public boolean isCoughVariant(MobEffect effect) {
        for (Supplier<MobEffect> variant : coughVariantGroup()) {
            if (variant.get() == effect) return true;
        }
        return false;
    }

    /** Cough, Bloody Coughing, and Productive Coughing present in this config — mutually exclusive. */
    public List<Supplier<MobEffect>> coughVariantGroup() {
        List<Supplier<MobEffect>> group = new ArrayList<>(3);
        addIfPresent(group, DiseaseEffects.COUGH);
        addIfPresent(group, DiseaseEffects.BLOODY_COUGHING);
        addIfPresent(group, DiseaseEffects.PRODUCTIVE_COUGHING);
        return group.size() < 2 ? List.of() : List.copyOf(group);
    }

    private void addIfPresent(List<Supplier<MobEffect>> group, Supplier<MobEffect> supplier) {
        MobEffect effect = supplier.get();
        if (indexOfEffect(effect) >= 0) group.add(supplier);
    }

    /**
     * Ordered bit indices on this config that match an active source symptom and may be inherited.
     */
    public List<Integer> inheritableFromSource(SymptomConfig sourceConfig, int sourceMask,
                                                Severity diseaseTier, int destMask) {
        List<Integer> result = new ArrayList<>();
        int bits = sourceConfig.symptomBits();
        for (int sb = 0; sb < bits; sb++) {
            if ((sourceMask & (1 << sb)) == 0) continue;
            MobEffect sourceEffect = sourceConfig.entryAt(sb).effect().get();
            int destBit = indexOfEffect(sourceEffect);
            if (destBit < 0) continue;
            if ((destMask & (1 << destBit)) != 0) continue;
            SymptomEntry dest = entryAt(destBit);
            if (!dest.band().eligibleAt(diseaseTier)) continue;
            result.add(destBit);
        }
        return result;
    }

    public static final Codec<SymptomConfig> CODEC = RecordCodecBuilder.create(i -> i.group(
        SymptomEntry.CODEC.listOf().fieldOf("hallmarks").forGetter(SymptomConfig::hallmarks),
        SymptomEntry.CODEC.listOf().optionalFieldOf("common_adds", List.of()).forGetter(SymptomConfig::commonAdds),
        SymptomEntry.CODEC.listOf().optionalFieldOf("severe", List.of()).forGetter(SymptomConfig::severe),
        SymptomSupersedes.CODEC.listOf().optionalFieldOf("exclusive_pairs", List.of()).forGetter(SymptomConfig::exclusivePairs),
        Codec.DOUBLE.listOf().fieldOf("thresholds").forGetter(SymptomConfig::thresholds),
        Codec.INT.fieldOf("min_interval").forGetter(SymptomConfig::minIntervalTicks),
        Codec.INT.fieldOf("max_interval").forGetter(SymptomConfig::maxIntervalTicks),
        Codec.INT.fieldOf("min_duration").forGetter(SymptomConfig::minDurationTicks),
        Codec.INT.fieldOf("max_duration").forGetter(SymptomConfig::maxDurationTicks),
        PersistentEffects.CODEC.optionalFieldOf("persistent", PersistentEffects.NONE).forGetter(SymptomConfig::persistentEffects)
    ).apply(i, (h, c, s, ex, t, mi, ma, md, mx, p) ->
            new SymptomConfig(h, c, s, ex, t, mi, ma, md, mx, p)));
}
