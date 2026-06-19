package com.theblackbaron.simplediseases.client.tooltip;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.DiseaseEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.MobEffectTextureManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

public record IconTextTooltipRenderer(IconTextTooltipComponent tooltip) implements ClientTooltipComponent {

    public static final ResourceLocation FEVER_ICON =
            ResourceLocation.fromNamespaceAndPath(SimpleDiseases.MOD_ID, "textures/mob_effect/fever.png");

    private static final int ICON_SIZE = 9;
    private static final int TEXT_OFFSET = 12;

    public static boolean isFeverLine(Component component) {
        return keyStartsWith(component, "simplediseases.fever.");
    }

    public static boolean isPainLine(Component component) {
        return keyStartsWith(component, "simplediseases.pain.");
    }

    public static boolean isSymptomLine(Component component) {
        return keyStartsWith(component, "simplediseases.symptom.");
    }

    @Nullable
    public static MobEffect symptomEffectFromLine(Component component) {
        if (!(component.getContents() instanceof TranslatableContents tc)) return null;
        String key = tc.getKey();
        if (!key.startsWith("simplediseases.symptom.")) return null;
        String path = key.substring("simplediseases.symptom.".length());
        return net.minecraftforge.registries.ForgeRegistries.MOB_EFFECTS.getValue(
                ResourceLocation.fromNamespaceAndPath(SimpleDiseases.MOD_ID, path));
    }

    @Nullable
    public static IconTextTooltipComponent fromLine(Component component) {
        if (isFeverLine(component)) {
            return new IconTextTooltipComponent(component, FEVER_ICON, null);
        }
        if (isPainLine(component)) {
            return new IconTextTooltipComponent(component, null, DiseaseEffects.PAIN.get());
        }
        if (isSymptomLine(component)) {
            MobEffect effect = symptomEffectFromLine(component);
            if (effect == null) return null;
            return new IconTextTooltipComponent(component, null, effect);
        }
        return null;
    }

    private static boolean keyStartsWith(Component component, String prefix) {
        if (!(component.getContents() instanceof TranslatableContents tc)) return false;
        return tc.getKey().startsWith(prefix);
    }

    @Override
    public int getHeight() {
        return ICON_SIZE + 2;
    }

    @Override
    public int getWidth(Font font) {
        return font.width(tooltip.line()) + TEXT_OFFSET;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics graphics) {
        ResourceLocation texture = tooltip.textureIcon();
        if (texture != null) {
            graphics.blit(texture, x, y, 0, 0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            return;
        }
        MobEffect effect = tooltip.effectIcon();
        if (effect != null) {
            MobEffectTextureManager textures = Minecraft.getInstance().getMobEffectTextures();
            graphics.blit(x, y, 0, ICON_SIZE, ICON_SIZE, textures.get(effect));
        }
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource buffer) {
        font.drawInBatch(tooltip.line(), x + TEXT_OFFSET, y + 1, -1, true, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 15728880);
    }
}
