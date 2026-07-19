package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import com.theblackbaron.simplediseases.status.category.DiseaseCategories;
import com.theblackbaron.simplediseases.status.category.DiseaseCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.function.Supplier;

/**
 * Definition schema for a wound-seeded bacterial disease: a symptomatic colonization phase
 * (accumulation while a flesh wound is open, decay when none exists) that latches at a threshold,
 * then deterministically worsens through severity tiers with recurring symptom episodes.
 *
 * <p>Triggered complications (e.g. sepsis triggered by cellulitis) are now registered as
 * {@link ComplicationDiseaseDef} with a {@code triggeredBy} field and handled by
 * {@link com.theblackbaron.simplediseases.status.category.ComplicationCategory}.
 */
public record BacterialDiseaseDef(
    ResourceLocation id,
    int              tierCount,
    ConditionType    organGroup,
    String           pathogenType,
    double           progressCap,
    double           latchThreshold,
    double           accumulationRate,
    double           recoveryRate,
    double           worseningRate,
    List<Double>     worseningThresholds,
    SymptomConfig    symptoms,
    String           caughtKey,
    String           curedKey,
    String           worsensKey
) implements DiseaseDef {

    @Override
    public DiseaseCategory category() {
        return DiseaseCategories.BACTERIAL;
    }

    public List<Severity> tiers() {
        return Severity.window(tierCount);
    }

    public Supplier<MobEffect> effectFor(Severity severity) {
        return DiseaseEffects.variant(id.getPath(), severity);
    }

    public void removeEffects(LivingEntity entity) {
        for (Severity sev : tiers()) {
            MobEffect effect = effectFor(sev).get();
            if (entity.hasEffect(effect)) entity.removeEffect(effect);
        }
    }

    public static final MapCodec<BacterialDiseaseDef> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
        ResourceLocation.CODEC.fieldOf("id").forGetter(BacterialDiseaseDef::id),
        Codec.INT.fieldOf("tier_count").forGetter(BacterialDiseaseDef::tierCount),
        ConditionType.CODEC.fieldOf("organ_group").forGetter(BacterialDiseaseDef::organGroup),
        Codec.STRING.fieldOf("pathogen_type").forGetter(BacterialDiseaseDef::pathogenType),
        Codec.DOUBLE.fieldOf("progress_cap").forGetter(BacterialDiseaseDef::progressCap),
        Codec.DOUBLE.fieldOf("latch_threshold").forGetter(BacterialDiseaseDef::latchThreshold),
        Codec.DOUBLE.fieldOf("accumulation_rate").forGetter(BacterialDiseaseDef::accumulationRate),
        Codec.DOUBLE.fieldOf("recovery_rate").forGetter(BacterialDiseaseDef::recoveryRate),
        Codec.DOUBLE.fieldOf("worsening_rate").forGetter(BacterialDiseaseDef::worseningRate),
        Codec.DOUBLE.listOf().fieldOf("worsening_thresholds").forGetter(BacterialDiseaseDef::worseningThresholds),
        SymptomConfig.CODEC.fieldOf("symptoms").forGetter(BacterialDiseaseDef::symptoms),
        Codec.STRING.fieldOf("caught_message").forGetter(BacterialDiseaseDef::caughtKey),
        Codec.STRING.fieldOf("cured_message").forGetter(BacterialDiseaseDef::curedKey),
        Codec.STRING.fieldOf("worsens_message").forGetter(BacterialDiseaseDef::worsensKey)
    ).apply(i, BacterialDiseaseDef::new));
}
