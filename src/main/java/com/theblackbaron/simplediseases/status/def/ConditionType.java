package com.theblackbaron.simplediseases.status.def;

import com.mojang.serialization.Codec;
import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Organ/system a disease affects — shown under the disease effect name in tooltips, and one half of
 * the {@link DiseaseDef#exclusionGroup()} composite key (paired with {@link DiseaseDef#pathogenType()}).
 */
public enum ConditionType {
    RESPIRATORY("simplediseases.condition.respiratory", "base_textures/respiratory_base"),
    GI("simplediseases.condition.gi", "base_textures/stomach_base"),
    TISSUE("simplediseases.condition.tissue", "base_textures/wound_base"),
    SYSTEMIC("simplediseases.condition.systemic", "base_textures/heart_base");

    private final String langKey;
    private final String texturePath;

    ConditionType(String langKey, String texturePath) {
        this.langKey = langKey;
        this.texturePath = texturePath;
    }

    public String langKey() {
        return langKey;
    }

    public ResourceLocation textureId() {
        return ResourceLocation.fromNamespaceAndPath(SimpleDiseases.MOD_ID, "textures/mob_effect/" + texturePath + ".png");
    }

    /** Serializes by lowercase name (e.g. "respiratory") — mirrors {@link Severity#CODEC}. */
    public static final Codec<ConditionType> CODEC =
            Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), ConditionType::id);

    public String id() { return name().toLowerCase(Locale.ROOT); }
}
