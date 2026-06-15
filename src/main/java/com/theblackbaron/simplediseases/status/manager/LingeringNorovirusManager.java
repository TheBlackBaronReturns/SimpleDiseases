package com.theblackbaron.simplediseases.status.manager;

import com.theblackbaron.simplediseases.particle.DiseaseParticles;
import com.theblackbaron.simplediseases.status.def.DiseaseRegistry;
import com.theblackbaron.simplediseases.status.def.ViralDiseaseDef;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks "lingering" norovirus puddles left in the world by vomiting players and infected villagers.
 * Each puddle is an ephemeral zone (position + expiry) that emits the norovirus particle and exposes
 * entities standing in it. State is in-memory only — puddles are short-lived (3 min) and do not survive
 * a server restart. The exposure itself lives with the domain owners: {@code DiseaseEvents} accumulates
 * norovirus on players standing in a zone (as if submerged in an infected reservoir), and
 * {@code ContagionManager} builds villager exposure from zones (the same way villagers catch any virus).
 */
public final class LingeringNorovirusManager {
    public static final long   DURATION_TICKS    = 3600L; // a puddle lasts 3 minutes
    public static final double RADIUS            = 4.5;   // horizontal reach of a puddle
    private static final double VERTICAL_REACH   = 1.5;   // how far above the puddle still counts
    private static final int    PARTICLE_INTERVAL = 8;    // ticks between particle pulses

    /** A single active puddle. */
    public static final class Zone {
        public final ServerLevel level;
        public final double x, y, z;
        public final long expiresAt;
        Zone(ServerLevel level, double x, double y, double z, long expiresAt) {
            this.level = level; this.x = x; this.y = y; this.z = z; this.expiresAt = expiresAt;
        }
    }

    private final List<Zone> zones = new ArrayList<>();

    public void place(ServerLevel level, double x, double y, double z, long gameTime) {
        zones.add(new Zone(level, x, y, z, gameTime + DURATION_TICKS));
    }

    /** Leaves a puddle at a vomiting player — only for norovirus (other diseases' vomiting is inert here). */
    public void onPlayerVomit(ServerPlayer player, ViralDiseaseDef vdef) {
        if (!vdef.id().equals(DiseaseRegistry.NOROVIRUS)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        place(level, player.getX(), player.getY(), player.getZ(), level.getGameTime());
    }

    /** Whether the entity is standing in any active norovirus puddle in its level. */
    public boolean isInZone(Entity entity) {
        if (zones.isEmpty()) return false;
        if (!(entity.level() instanceof ServerLevel level)) return false;
        double ex = entity.getX(), ey = entity.getY(), ez = entity.getZ();
        for (Zone z : zones) {
            if (z.level != level) continue;
            double dx = ex - z.x, dz = ez - z.z;
            if (dx * dx + dz * dz <= RADIUS * RADIUS && Math.abs(ey - z.y) <= VERTICAL_REACH) return true;
        }
        return false;
    }

    /** Whether a level currently has any active puddles. Lets callers skip their own scans cheaply. */
    public boolean hasZonesIn(ServerLevel level) {
        for (Zone z : zones) {
            if (z.level == level) return true;
        }
        return false;
    }

    /** Prunes expired puddles and emits the norovirus particle from the live ones. */
    public void tick(MinecraftServer server, long gameTime) {
        if (zones.isEmpty()) return;
        boolean emit = gameTime % PARTICLE_INTERVAL == 0;
        Iterator<Zone> it = zones.iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            if (gameTime >= z.expiresAt) { it.remove(); continue; }
            if (emit) {
                z.level.sendParticles(DiseaseParticles.NOROVIRUS.get(),
                        z.x, z.y + 0.1, z.z, 3, RADIUS * 0.5, 0.1, RADIUS * 0.5, 0.01);
            }
        }
    }
}
