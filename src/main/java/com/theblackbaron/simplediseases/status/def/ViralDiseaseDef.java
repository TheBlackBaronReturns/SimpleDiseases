package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.category.DiseaseCategories;
import com.theblackbaron.simplediseases.status.category.DiseaseCategory;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.function.Supplier;

/**
 * Definition schema for a viral disease: scalar accumulation that latches at a threshold, drains
 * during recovery, grants timed immunity, manifests symptom episodes, and spreads via contagion.
 *
 * <p>Severity is a per-illness {@link Severity} rolled when accumulation first reaches the lowest
 * symptom threshold (see ViralCategory). Rather than a single effect, the disease has one named effect
 * variant per tier in its {@link Severity#window} ({@code tierCount} of 3 or 4); the variant carries
 * the tier-scaled debuffs and the "Mild/Severe …" name. The symptom pacing on {@link SymptomConfig}
 * is the Moderate baseline, scaled per tier by the Severity multipliers.
 */
public record ViralDiseaseDef(
    ResourceLocation          id,
    int                       tierCount,           // 3 or 5; centered window of the Severity scale
    Supplier<ParticleOptions> particle,
    String                    exclusionGroup,
    double              progressCap,
    double              latchThreshold,
    double              recoveryRate,
    long                immunityTicks,
    SymptomConfig       symptoms,
    ViralContagion      contagion,
    AcquisitionRule     acquisition,         // how this disease is picked on a damp/windchill exposure
    String              caughtKey,
    String              curedKey,
    // "Committed incubation on exposure" range (the unified incubation model — see DiseaseEvents/ContagionManager).
    // incubationMin/incubationMax are the NORMAL roll range; immunodeficiency shifts it up to [incubationMax, 2×incubationMax].
    // Norovirus uses it for reservoir/puddle entry; cold/flu/rsv use it for P→P / V→P contact.
    double              incubationMin,
    double              incubationMax
) implements DiseaseDef {

    @Override
    public DiseaseCategory category() {
        return DiseaseCategories.VIRAL;
    }

    /** Rolls a committed exposure incubation for this disease. Normal range [incubationMin, incubationMax]; an
     *  immunodeficient receiver rolls the harsher [incubationMax, 2×incubationMax] (a single bad exposure can latch
     *  outright). The incubation then bleeds into this disease's progress over time (see the delivery loop). */
    public double rollIncubation(net.minecraft.util.RandomSource rng, boolean immunodeficient) {
        double min = immunodeficient ? incubationMax : incubationMin;
        double max = immunodeficient ? incubationMax * 2.0 : incubationMax;
        return min + rng.nextDouble() * (max - min);
    }

    /** The tiers this disease spans (centered on Moderate). */
    public List<Severity> tiers() {
        return Severity.window(tierCount);
    }

    /** Lowest (mildest) tier in the span — the floor for treatment reductions. */
    public Severity lowestSeverity() {
        return tiers().get(0);
    }

    /** The named effect variant for a tier. */
    public Supplier<MobEffect> effectFor(Severity severity) {
        return DiseaseEffects.variant(id.getPath(), severity);
    }

    /** The variant villagers carry — always Moderate, the universal middle. */
    public Supplier<MobEffect> villagerEffect() {
        return effectFor(Severity.MODERATE);
    }

    /** True if the entity has any of this disease's tier variants. */
    public boolean hasAnyEffect(LivingEntity entity) {
        for (Severity sev : tiers()) {
            if (entity.hasEffect(effectFor(sev).get())) return true;
        }
        return false;
    }

    /** The active tier-variant effect instance on the entity, or null if none is present. Lets
     *  callers read whichever tier an entity carries without assuming Moderate. */
    public MobEffectInstance activeEffect(LivingEntity entity) {
        for (Severity sev : tiers()) {
            MobEffectInstance inst = entity.getEffect(effectFor(sev).get());
            if (inst != null) return inst;
        }
        return null;
    }

    /** Which tier variant the entity currently carries, or null if none — the {@link #activeEffect}'s tier. */
    public Severity activeSeverity(LivingEntity entity) {
        for (Severity sev : tiers()) {
            if (entity.hasEffect(effectFor(sev).get())) return sev;
        }
        return null;
    }

    /** Removes whichever tier variant the entity currently has. */
    public void removeEffects(LivingEntity entity) {
        for (Severity sev : tiers()) {
            MobEffect effect = effectFor(sev).get();
            if (entity.hasEffect(effect)) entity.removeEffect(effect);
        }
    }

    public static final MapCodec<ViralDiseaseDef> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(ViralDiseaseDef::id),
        com.mojang.serialization.Codec.INT.fieldOf("tier_count").forGetter(ViralDiseaseDef::tierCount),
        DiseaseCodecs.PARTICLE.fieldOf("particle").forGetter(ViralDiseaseDef::particle),
        com.mojang.serialization.Codec.STRING.fieldOf("exclusion_group").forGetter(ViralDiseaseDef::exclusionGroup),
        com.mojang.serialization.Codec.DOUBLE.fieldOf("progress_cap").forGetter(ViralDiseaseDef::progressCap),
        com.mojang.serialization.Codec.DOUBLE.fieldOf("latch_threshold").forGetter(ViralDiseaseDef::latchThreshold),
        com.mojang.serialization.Codec.DOUBLE.fieldOf("recovery_rate").forGetter(ViralDiseaseDef::recoveryRate),
        com.mojang.serialization.Codec.LONG.fieldOf("immunity_ticks").forGetter(ViralDiseaseDef::immunityTicks),
        SymptomConfig.CODEC.fieldOf("symptoms").forGetter(ViralDiseaseDef::symptoms),
        ViralContagion.CODEC.fieldOf("contagion").forGetter(ViralDiseaseDef::contagion),
        AcquisitionRule.CODEC.fieldOf("acquisition").forGetter(ViralDiseaseDef::acquisition),
        com.mojang.serialization.Codec.STRING.fieldOf("caught_message").forGetter(ViralDiseaseDef::caughtKey),
        com.mojang.serialization.Codec.STRING.fieldOf("cured_message").forGetter(ViralDiseaseDef::curedKey),
        com.mojang.serialization.Codec.DOUBLE.fieldOf("incubation_min").forGetter(ViralDiseaseDef::incubationMin),
        com.mojang.serialization.Codec.DOUBLE.fieldOf("incubation_max").forGetter(ViralDiseaseDef::incubationMax)
    ).apply(i, ViralDiseaseDef::new));
}
