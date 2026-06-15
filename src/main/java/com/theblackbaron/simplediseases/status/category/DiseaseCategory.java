package com.theblackbaron.simplediseases.status.category;

import com.mojang.serialization.MapCodec;
import com.theblackbaron.simplediseases.status.component.ComponentType;
import com.theblackbaron.simplediseases.status.component.DiseaseInstance;
import com.theblackbaron.simplediseases.status.def.DiseaseDef;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * A disease archetype — the fixed mechanics shared by every disease of a family (viral
 * accumulate→latch→contagion, bacterial latch→incubate→onset, fungal filth-driven, parasitic
 * four-stage). This is the open extension point: a new family is a new {@code DiseaseCategory}
 * registered in {@link DiseaseCategories}, with no change to the core loop or to peer categories.
 *
 * <p>Behavior lives here in Java; individual diseases are data ({@link DiseaseDef}) that tune only
 * what the category chooses to expose. The category owns its own definition schema ({@link
 * #defCodec()}) and the set of state components its diseases carry ({@link #componentTypes()}).
 */
public interface DiseaseCategory {

    /** Stable id (e.g. {@code simplediseases:viral}); the {@code "category"} key in disease JSON. */
    ResourceLocation id();

    /** Codec for this category's disease definitions — the schema a datapack author fills in. */
    MapCodec<? extends DiseaseDef> defCodec();

    /** State components a disease of this category needs, instantiated on its {@link DiseaseInstance}. */
    Set<ComponentType<?>> componentTypes();

    /** Whether diseases of this family spread between entities (only viral, today). */
    boolean contagious();

    /** Advance one disease of this category for one player-tick. */
    void tick(DiseaseDef def, DiseaseInstance instance, DiseaseContext ctx);
}
