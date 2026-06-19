package com.theblackbaron.simplediseases.client.tooltip;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.jetbrains.annotations.Nullable;

/** Tooltip row pairing an optional texture or mob-effect icon with description text. */
public record IconTextTooltipComponent(
        Component line,
        @Nullable ResourceLocation textureIcon,
        @Nullable MobEffect effectIcon
) implements TooltipComponent {}
