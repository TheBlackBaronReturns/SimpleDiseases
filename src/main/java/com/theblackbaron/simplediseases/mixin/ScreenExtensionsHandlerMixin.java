package com.theblackbaron.simplediseases.mixin;

import com.mojang.datafixers.util.Either;
import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipComponent;
import com.theblackbaron.simplediseases.client.tooltip.IconTextTooltipRenderer;
import net.mehvahdjukaar.jeed.common.EffectRenderer;
import net.mehvahdjukaar.jeed.common.ScreenExtensionsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = ScreenExtensionsHandler.class, remap = false)
public abstract class ScreenExtensionsHandlerMixin {

    @Inject(method = "renderEffectTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sd$renderIconTooltipRows(
            MobEffectInstance effect, Screen screen, GuiGraphics graphics, int x, int y, boolean showDuration,
            CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
        List<Component> lines = EffectRenderer.getTooltipsWithDescription(effect, flag, true, showDuration);
        if (lines.isEmpty()) return;

        List<Either<FormattedText, TooltipComponent>> elements = new ArrayList<>(lines.size());
        for (Component line : lines) {
            IconTextTooltipComponent iconRow = IconTextTooltipRenderer.fromLine(line);
            if (iconRow != null) {
                elements.add(Either.right(iconRow));
            } else {
                elements.add(Either.left(line));
            }
        }

        graphics.renderComponentTooltipFromElements(mc.font, elements, x, y, ItemStack.EMPTY);
        ci.cancel();
    }
}
