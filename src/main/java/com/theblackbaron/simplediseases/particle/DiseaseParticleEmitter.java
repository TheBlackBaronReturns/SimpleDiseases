package com.theblackbaron.simplediseases.particle;

import com.theblackbaron.simplediseases.status.manager.PlayerInjuryState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Emits a disease's particle from an afflicted entity (player or villager). The particle is supplied
 * by the disease definition, so one emitter serves every disease — the cold and flu emitters were
 * byte-identical except for which particle they sent.
 */
public final class DiseaseParticleEmitter {
    private static final int EMIT_INTERVAL_TICKS = 8;
    private static final int BLEED_EMIT_COOLDOWN = 3;

    private static final Map<UUID, Long> lastVomitEmitTick = new HashMap<>();

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

    public static void emitBleeding(ServerPlayer player, PlayerInjuryState injury, int amplifier, long gameTime) {
        if (gameTime - injury.lastBleedEmitTick() < BLEED_EMIT_COOLDOWN) return;
        injury.setLastBleedEmitTick(gameTime);

        double walkDelta = injury.recordBleedParticleWalk(player);
        int count = Math.round(0.5F + 0.5F * (15.0F + amplifier) * (float) walkDelta);
        if (count <= 0) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        // Majrusz parity: count formula, 0.15s cooldown, sizeBased center, 0.25 spread.
        double spreadX = player.getBbWidth() * 0.25D;
        double spreadY = player.getBbHeight() * 0.25D;
        double spreadZ = player.getBbWidth() * 0.25D;
        double speed = Mth.lerp(player.getRandom().nextFloat(), 0.025D, 0.075D);
        level.sendParticles(DiseaseParticles.BLEEDING.get(),
                player.getX(), player.getY() + 0.5D * player.getBbHeight(), player.getZ(),
                count, spreadX, spreadY, spreadZ, speed);
    }

    public static void emitVomiting(ServerPlayer player, long gameTime) {
        UUID id = player.getUUID();
        if (gameTime - lastVomitEmitTick.getOrDefault(id, 0L) < BLEED_EMIT_COOLDOWN) return;
        lastVomitEmitTick.put(id, gameTime);

        if (!(player.level() instanceof ServerLevel level)) return;

        double spreadX = player.getBbWidth() * 0.25D;
        double spreadY = player.getBbHeight() * 0.10D;
        double spreadZ = player.getBbWidth() * 0.25D;
        double speed = Mth.lerp(player.getRandom().nextFloat(), 0.025D, 0.075D);
        int count = 2 + player.getRandom().nextInt(3);
        level.sendParticles(DiseaseParticles.VOMIT.get(),
                player.getX(), player.getY() + player.getBbHeight() * 0.35D, player.getZ(),
                count, spreadX, spreadY, spreadZ, speed);
    }

    public static void clearVomitEmitState(UUID playerId) {
        lastVomitEmitTick.remove(playerId);
    }
}
