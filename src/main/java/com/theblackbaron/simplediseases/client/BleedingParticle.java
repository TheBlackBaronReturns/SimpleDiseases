package com.theblackbaron.simplediseases.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class BleedingParticle extends TextureSheetParticle {
    private final SpriteSet sprites;
    private final float yOffset;
    private final Quaternionf onGroundQuaternion;
    private final float baseColor;

    protected BleedingParticle(ClientLevel level, double x, double y, double z,
                               double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        float randomRatio = this.random.nextFloat();
        this.xd = xSpeed + Mth.lerp(this.random.nextDouble(), -0.05D, 0.05D);
        this.yd = ySpeed * 0.5D;
        this.zd = zSpeed + Mth.lerp(this.random.nextDouble(), -0.05D, 0.05D);
        this.lifetime = (int) (800.0F * Mth.lerp(randomRatio, 0.8F, 1.0F));
        this.age = (int) (this.lifetime * Mth.lerp(randomRatio, 0.0F, 0.5F));
        this.quadSize = 0.1F;
        this.yOffset = Mth.lerp(randomRatio, 0.001F, 0.005F);
        this.onGroundQuaternion = Axis.XP.rotation(Mth.HALF_PI)
                .rotateZ((int) (randomRatio * 4.0F) * Mth.HALF_PI);
        this.baseColor = Mth.lerp(randomRatio, 0.8F, 1.0F);
        this.setSpriteFromAge(this.sprites);
        updateColor();
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            this.move(this.xd, this.yd, this.zd);
            this.yd = this.yd - (this.onGround ? 0.0D : 0.0375D);
            this.xd = this.xd * (this.onGround ? 0.5D : 0.95D);
            this.zd = this.zd * (this.onGround ? 0.5D : 0.95D);
            this.setSpriteFromAge(this.sprites);
            updateColor();
        }
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        Vec3 cameraPos = camera.getPosition();
        float x = (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x());
        float y = (float) (Mth.lerp(partialTick, this.yo, this.y) - cameraPos.y()) + this.yOffset;
        float z = (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z());

        Quaternionf rotation;
        if (this.onGround) {
            rotation = this.onGroundQuaternion;
        } else if (this.roll == 0.0F) {
            rotation = camera.rotation();
        } else {
            rotation = new Quaternionf(camera.rotation());
            rotation.mul(Axis.ZP.rotation(Mth.lerp(partialTick, this.oRoll, this.roll)));
        }

        Vector3f v0 = new Vector3f(-1.0F, -1.0F, 0.0F);
        Vector3f v1 = new Vector3f(-1.0F, 1.0F, 0.0F);
        Vector3f v2 = new Vector3f(1.0F, 1.0F, 0.0F);
        Vector3f v3 = new Vector3f(1.0F, -1.0F, 0.0F);
        v0.rotate(rotation);
        v1.rotate(rotation);
        v2.rotate(rotation);
        v3.rotate(rotation);

        float size = this.getQuadSize(partialTick);
        v0.mul(size);
        v1.mul(size);
        v2.mul(size);
        v3.mul(size);

        int light = this.getLightColor(partialTick);
        buffer.vertex(x + v0.x(), y + v0.y(), z + v0.z()).uv(this.getU1(), this.getV1())
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x + v1.x(), y + v1.y(), z + v1.z()).uv(this.getU1(), this.getV0())
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x + v2.x(), y + v2.y(), z + v2.z()).uv(this.getU0(), this.getV0())
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
        buffer.vertex(x + v3.x(), y + v3.y(), z + v3.z()).uv(this.getU0(), this.getV1())
                .color(this.rCol, this.gCol, this.bCol, this.alpha).uv2(light).endVertex();
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.quadSize * 1.5F;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_OPAQUE;
    }

    private void updateColor() {
        float color = this.baseColor * (0.4F + 0.6F * (1.0F - (float) this.age / (float) this.lifetime));
        this.setColor(color, color, color);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Nullable
        @Override
        public BleedingParticle createParticle(SimpleParticleType type, ClientLevel level,
                                               double x, double y, double z,
                                               double xSpeed, double ySpeed, double zSpeed) {
            return new BleedingParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.sprites);
        }
    }
}
