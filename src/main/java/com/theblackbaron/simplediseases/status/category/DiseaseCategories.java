package com.theblackbaron.simplediseases.status.category;

import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Code-level registry of disease archetypes. Adding a future family (e.g. bacterial) is a single
 * {@link #register} call here plus the category class — the engine, contagion, and other categories
 * are untouched.
 */
public final class DiseaseCategories {
    private DiseaseCategories() {}

    private static final Map<ResourceLocation, DiseaseCategory> BY_ID = new LinkedHashMap<>();

    public static final DiseaseCategory VIRAL = register(new ViralCategory());

    /** Viral complications (pneumonia): develop from another disease, replace it, carry its contagion. */
    public static final DiseaseCategory COMPLICATION = register(new ComplicationCategory());

    /** Wound-seeded bacterial infections: colonization phase then deterministic severity worsening. */
    public static final DiseaseCategory BACTERIAL = register(new BacterialCategory());

    public static DiseaseCategory register(DiseaseCategory category) {
        BY_ID.put(category.id(), category);
        return category;
    }

    public static DiseaseCategory get(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public static Collection<DiseaseCategory> all() {
        return BY_ID.values();
    }

    /** Serializes a category as its id — backs the {@code "category"} dispatch in disease JSON. */
    public static final Codec<DiseaseCategory> CODEC =
        ResourceLocation.CODEC.xmap(DiseaseCategories::get, DiseaseCategory::id);
}
