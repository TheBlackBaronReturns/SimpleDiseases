package com.theblackbaron.simplediseases.status.manager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

public final class PlayerInjuryState {
    private static final String KEY_FLESH_WOUND_TICKS = "fleshWoundTicks";

    private int    fleshWoundTicks  = 0;
    private double lastBleedParticleX  = Double.NaN;
    private double lastBleedParticleZ  = Double.NaN;
    private long   lastBleedEmitTick   = 0L;

    public int    fleshWoundTicks()  { return fleshWoundTicks; }
    public long   lastBleedEmitTick() { return lastBleedEmitTick; }

    public void setLastBleedEmitTick(long tick) { this.lastBleedEmitTick = tick; }

    public int fleshWoundSeverity() {
        if (fleshWoundTicks > 6000) return 2;
        if (fleshWoundTicks > 3000) return 1;
        return fleshWoundTicks > 0 ? 0 : -1;
    }

    public boolean hasActiveInjury() {
        return fleshWoundTicks > 0;
    }

    public void clearFleshWound() {
        fleshWoundTicks = 0;
    }

    public void addFleshWound(int severity) {
        int duration = switch (Math.max(0, Math.min(2, severity))) {
            case 2 -> 9000;
            case 1 -> 6000;
            default -> 3000;
        };
        fleshWoundTicks = Math.max(fleshWoundTicks, duration);
    }

    /** Horizontal distance walked since the last bleeding particle emit; updates stored position. */
    public double recordBleedParticleWalk(ServerPlayer player) {
        double x = player.getX();
        double z = player.getZ();
        if (Double.isNaN(lastBleedParticleX) || Double.isNaN(lastBleedParticleZ)) {
            lastBleedParticleX = x;
            lastBleedParticleZ = z;
            return 0.0;
        }
        double dx = x - lastBleedParticleX;
        double dz = z - lastBleedParticleZ;
        lastBleedParticleX = x;
        lastBleedParticleZ = z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public void tick() {
        if (fleshWoundTicks > 0) fleshWoundTicks--;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (fleshWoundTicks > 0)    tag.putInt(KEY_FLESH_WOUND_TICKS, fleshWoundTicks);
        return tag;
    }

    public static PlayerInjuryState load(CompoundTag tag) {
        PlayerInjuryState state = new PlayerInjuryState();
        state.fleshWoundTicks = tag.contains(KEY_FLESH_WOUND_TICKS, Tag.TAG_INT)
                ? Math.max(0, tag.getInt(KEY_FLESH_WOUND_TICKS)) : 0;
        return state;
    }

    public PlayerInjuryState copy() {
        PlayerInjuryState copy = new PlayerInjuryState();
        copy.fleshWoundTicks  = fleshWoundTicks;
        copy.lastBleedParticleX = lastBleedParticleX;
        copy.lastBleedParticleZ = lastBleedParticleZ;
        copy.lastBleedEmitTick = lastBleedEmitTick;
        return copy;
    }

    public void reset() {
        fleshWoundTicks  = 0;
        lastBleedParticleX = Double.NaN;
        lastBleedParticleZ = Double.NaN;
        lastBleedEmitTick = 0L;
    }
}
