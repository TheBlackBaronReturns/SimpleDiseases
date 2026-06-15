package com.theblackbaron.simplediseases.particle;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Emits a disease's particle from an afflicted entity (player or villager). The particle is supplied
 * by the disease definition, so one emitter serves every disease — the cold and flu emitters were
 * byte-identical except for which particle they sent.
 */
public final class DiseaseParticleEmitter {
    private static final int EMIT_INTERVAL_TICKS = 8;

    private DiseaseParticleEmitter() {}

    public static void tick(LivingEntity entity, ParticleOptions particle) {
        if (entity.tickCount % EMIT_INTERVAL_TICKS != 0) return;
        if (!(entity.level() instanceof ServerLevel level)) return;

        double x     = entity.getX();
        double y     = entity.getEyeY() - 0.15D;
        double z     = entity.getZ();
        double width = Math.max(0.25D, entity.getBbWidth() * 0.35D);

        level.sendParticles(particle, x, y, z, 2, width, 0.25D, width, 0.003D);
    }
}
