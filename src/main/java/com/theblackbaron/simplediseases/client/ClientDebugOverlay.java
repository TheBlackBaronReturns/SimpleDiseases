package com.theblackbaron.simplediseases.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Multi-line HUD for /sdviral and /sdbacterial debug output. */
public final class ClientDebugOverlay {
    private static List<String> viralLines = List.of();
    private static List<String> bacterialLines = List.of();

    private ClientDebugOverlay() {}

    public static void update(int updateMask, List<String> viral, List<String> bacterial) {
        if ((updateMask & 1) != 0) {
            viralLines = copyOrEmpty(viral);
        }
        if ((updateMask & 2) != 0) {
            bacterialLines = copyOrEmpty(bacterial);
        }
    }

    public static void clearAll() {
        viralLines = List.of();
        bacterialLines = List.of();
    }

    public static boolean isActive() {
        return !viralLines.isEmpty() || !bacterialLines.isEmpty();
    }

    public static void render(GuiGraphics graphics, Font font) {
        if (!isActive()) return;

        int x = 2;
        int y = 2;
        int lineHeight = font.lineHeight;

        y = drawSection(graphics, font, viralLines, x, y, lineHeight);
        if (!viralLines.isEmpty() && !bacterialLines.isEmpty()) {
            y += 2;
        }
        drawSection(graphics, font, bacterialLines, x, y, lineHeight);
    }

    private static int drawSection(GuiGraphics graphics, Font font, List<String> lines, int x, int y, int lineHeight) {
        for (String line : lines) {
            if (line == null || line.isEmpty()) continue;
            graphics.drawString(font, Component.literal(line), x, y, 0xFFFFFF, true);
            y += lineHeight;
        }
        return y;
    }

    private static List<String> copyOrEmpty(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of();
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }
}
