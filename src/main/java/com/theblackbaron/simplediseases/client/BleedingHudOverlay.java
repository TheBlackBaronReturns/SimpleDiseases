package com.theblackbaron.simplediseases.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;

public final class BleedingHudOverlay {
    private static final int GRID_WIDTH  = 6;
    private static final int GRID_HEIGHT = 4;
    private static final int LIFETIME    = 9 * 20;
    private static final int ASSETS_COUNT = 7;

    private static final List<ResourceLocation> ASSETS = new ArrayList<>();
    private static final List<Splatter> SPLATTERS = new ArrayList<>();

    static {
        for (int i = 0; i < ASSETS_COUNT; i++) {
            ASSETS.add(ResourceLocation.fromNamespaceAndPath(SimpleDiseases.MOD_ID, "textures/particle/blood_" + i + ".png"));
        }
        for (int x = 0; x < GRID_WIDTH; x++) {
            for (int y = 0; y < GRID_HEIGHT; y++) {
                SPLATTERS.add(new Splatter(x, y));
            }
        }
    }

    private BleedingHudOverlay() {}

    public static void addSplatter(int count) {
        List<Integer> xs = randomizedCoordinates(GRID_WIDTH);
        List<Integer> ys = randomizedCoordinates(GRID_HEIGHT);
        for (int i = 0; i < count && i < xs.size(); i++) {
            SPLATTERS.get(xs.get(i) * GRID_HEIGHT + ys.get(i)).makeVisible();
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        SPLATTERS.forEach(Splatter::tick);
    }

    public static void render(GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        for (Splatter splatter : SPLATTERS) {
            if (splatter.hasFinished()) continue;
            float color = splatter.getColor();
            Splatter.RenderData data = splatter.buildRenderData(screenWidth, screenHeight);
            RenderSystem.setShaderColor(color, color, color, splatter.getAlpha());
            RenderSystem.setShaderTexture(0, data.resource());
            graphics.blit(data.resource(), data.x(), data.y(), 0, 0, data.size(), data.size(), data.size(), data.size());
        }
        RenderSystem.disableBlend();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static List<Integer> randomizedCoordinates(int max) {
        List<Integer> coords = new ArrayList<>();
        for (int i = 0; i < max; i++) coords.add(i);
        RandomSource random = Minecraft.getInstance().level != null
                ? Minecraft.getInstance().level.random
                : RandomSource.create();
        for (int i = coords.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = coords.get(i);
            coords.set(i, coords.get(j));
            coords.set(j, tmp);
        }
        return coords;
    }

    private static final class Splatter {
        private final int gridX;
        private final int gridY;
        private int ticks;
        private int phase;

        Splatter(int gridX, int gridY) {
            this.gridX = gridX;
            this.gridY = gridY;
            this.ticks = LIFETIME;
        }

        void makeVisible() {
            if (!hasFinished()) return;
            RandomSource random = Minecraft.getInstance().level != null
                    ? Minecraft.getInstance().level.random
                    : RandomSource.create();
            this.ticks = random.nextInt(2 * 20);
            this.phase = random.nextInt(ASSETS_COUNT);
        }

        void tick() {
            if (!hasFinished()) ticks++;
        }

        boolean hasFinished() {
            return ticks >= LIFETIME;
        }

        float getColor() {
            float ratio = (float) ticks / LIFETIME;
            return Mth.lerp(ratio, 1.0F, 0.6F);
        }

        float getAlpha() {
            float ratio = (float) ticks / LIFETIME;
            return Mth.clamp(0.7F * (1.0F - ratio * ratio), 0.0F, 0.7F);
        }

        RenderData buildRenderData(int width, int height) {
            int size = (int) (height / (GRID_HEIGHT * 1.5F));
            int x = (int) (gridX * size + (gridX >= GRID_WIDTH / 2 ? width - GRID_WIDTH * size : 0));
            int y = (int) ((1.5F * gridY + (gridX % 2 == 0 ? 0.0F : 0.5F)) * size);
            return new RenderData(x, y, size, ASSETS.get(phase));
        }

        record RenderData(int x, int y, int size, ResourceLocation resource) {}
    }
}
