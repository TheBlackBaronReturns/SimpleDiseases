package com.theblackbaron.simplediseases.status.def;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Organ/system target shown under the disease effect name in tooltips. */
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

    public static Optional<ConditionType> forDisease(ResourceLocation diseaseId) {
        return switch (diseaseId.getPath()) {
            case "cold", "flu", "rsv", "pneumonia", "bronchitis" -> Optional.of(RESPIRATORY);
            case "norovirus" -> Optional.of(GI);
            case "cellulitis_staph" -> Optional.of(TISSUE);
            case "sepsis_staph", "mof_staph" -> Optional.of(SYSTEMIC);
            default -> Optional.empty();
        };
    }
}
