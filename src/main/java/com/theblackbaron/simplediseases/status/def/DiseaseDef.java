package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.theblackbaron.simplediseases.status.category.DiseaseCategories;
import com.theblackbaron.simplediseases.status.category.DiseaseCategory;
import net.minecraft.resources.ResourceLocation;

/**
 * Static, immutable definition of a single disease. The {@code "category"} field dispatches the
 * rest of the schema to that category's {@link DiseaseCategory#defCodec()} — so a viral disease and
 * a parasite disease share this dispatch but have entirely different bodies. Built in code today
 * ({@link DiseaseRegistry}); loadable from {@code data/<ns>/disease/*.json} later via {@link #CODEC}.
 * How a disease is represented as MobEffect(s) is category-specific (viral uses per-tier variants).
 */
public interface DiseaseDef {

    DiseaseCategory category();

    ResourceLocation id();

    /** Organ system this disease affects (respiratory/GI/tissue/systemic) — half of the exclusion key. */
    ConditionType organGroup();

    /** Pathogen biology ("viral"/"bacterial", {@link DiseaseRegistry#GROUP_VIRAL}/{@link DiseaseRegistry#GROUP_BACTERIAL}).
     *  Drives ColdSweat recovery-warmth thresholds, the environmental complication-worsening gate, and
     *  debug-panel bucketing — NOT mutual exclusion. Use {@link #exclusionGroup()} for that. */
    String pathogenType();

    /** Composite mutual-exclusion / immunity-window key: two diseases only exclude each other (and only
     *  share a post-recovery immunity window) when they match on BOTH organ and pathogen. Derived, not
     *  stored, so it can never disagree with {@link #organGroup()}/{@link #pathogenType()}. */
    default String exclusionGroup() {
        return organGroup().id() + "_" + pathogenType();
    }

    String caughtKey();

    String curedKey();

    /** Dispatch by category id to the category's own definition schema. */
    Codec<DiseaseDef> CODEC = DiseaseCategories.CODEC.dispatch(
        "category", DiseaseDef::category, category -> category.defCodec().codec());
}
