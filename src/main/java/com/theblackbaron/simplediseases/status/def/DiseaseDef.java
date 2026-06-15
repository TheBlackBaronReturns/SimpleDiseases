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

    /** Diseases sharing an exclusion group can't be active simultaneously (cold and flu: respiratory). */
    String exclusionGroup();

    String caughtKey();

    String curedKey();

    /** Dispatch by category id to the category's own definition schema. */
    Codec<DiseaseDef> CODEC = DiseaseCategories.CODEC.dispatch(
        "category", DiseaseDef::category, category -> category.defCodec().codec());
}
