package com.theblackbaron.simplediseases.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.Nullable;

public class ColdParticle extends TextureSheetParticle {
    private final SpriteSet sprites;

    protected ColdParticle(ClientLevel level, double x, double y, double z,
                           double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.xd = xSpeed + (this.random.nextDouble() - 0.5D) * 0.01D;
        this.yd = ySpeed + 0.01D + this.random.nextDouble() * 0.015D;
        this.zd = zSpeed + (this.random.nextDouble() - 0.5D) * 0.01D;
        this.quadSize = 0.20F + this.random.nextFloat() * 0.12F;
        this.lifetime = 28 + this.random.nextInt(18);
        this.gravity = -0.002F;
        this.friction = 0.88F;
        this.alpha = 0.72F;
        // No colour tint — render the texture as authored.
        this.pickSprite(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        this.pickSprite(this.sprites);
        this.alpha = 0.72F * (1.0F - (float) this.age / (float) this.lifetime);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public ColdParticle createParticle(SimpleParticleType type, ClientLevel level,
                                           double x, double y, double z,
                                           double xSpeed, double ySpeed, double zSpeed) {
            return new ColdParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
