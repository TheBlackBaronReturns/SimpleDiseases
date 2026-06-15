package com.theblackbaron.simplediseases.status.manager;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

public final class PlayerInjuryState {
    private static final String KEY_BLEEDING          = "bleeding";
    private static final String KEY_INTERNAL_BLEEDING = "internalBleeding";
    private static final String KEY_FLESH_WOUND_TICKS = "fleshWoundTicks";

    private static final double MAX_BLEEDING = 5.0;

    private double bleeding         = 0.0;
    private double internalBleeding = 0.0;
    private int    fleshWoundTicks  = 0;

    public double bleeding()         { return bleeding; }
    public double internalBleeding() { return internalBleeding; }
    public int    fleshWoundTicks()  { return fleshWoundTicks; }

    public int fleshWoundSeverity() {
        if (fleshWoundTicks > 6000) return 2;
        if (fleshWoundTicks > 3000) return 1;
        return fleshWoundTicks > 0 ? 0 : -1;
    }

    public double totalWoundLoad() {
        return bleeding + internalBleeding * 1.5;
    }

    public boolean hasActiveInjury() {
        return bleeding > 0.0 || internalBleeding > 0.0 || fleshWoundTicks > 0;
    }

    public void addBleeding(double amount) {
        if (Double.isNaN(amount) || amount == 0.0) return;
        if (amount >= 1.0 && amount <= 2.0) amount = Math.max(1.0, amount * 0.75);
        bleeding = clamp(bleeding + amount, 0.0, MAX_BLEEDING);
    }

    public void addInternalBleeding(double amount) {
        if (Double.isNaN(amount) || amount <= 0.0) return;
        internalBleeding = clamp(internalBleeding + amount, 0.0, MAX_BLEEDING);
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

    public void tick() {
        double floor = bleedingFloor();
        if (fleshWoundTicks > 0) {
            fleshWoundTicks--;
            if (bleeding < floor) bleeding = floor;
        }
        if (bleeding > 0.0) {
            // Three-tier decay: fast below 1.0, slow below 3.0, very slow at 3.0+ (no freeze)
            double rate = bleeding < 1.0 ? 0.005 : (bleeding < 3.0 ? 0.001 : 0.0002);
            bleeding = Math.max(floor, bleeding - rate);
            if (bleeding < 0.000001) bleeding = 0.0;
        }
        if (internalBleeding > 0.0) {
            double rate = internalBleeding < 1.0 ? 0.0025 : 0.0005;
            internalBleeding = Math.max(0.0, internalBleeding - rate);
            if (internalBleeding < 0.000001) internalBleeding = 0.0;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (bleeding > 0.0)         tag.putDouble(KEY_BLEEDING, bleeding);
        if (internalBleeding > 0.0) tag.putDouble(KEY_INTERNAL_BLEEDING, internalBleeding);
        if (fleshWoundTicks > 0)    tag.putInt(KEY_FLESH_WOUND_TICKS, fleshWoundTicks);
        return tag;
    }

    public static PlayerInjuryState load(CompoundTag tag) {
        PlayerInjuryState state = new PlayerInjuryState();
        state.bleeding = tag.contains(KEY_BLEEDING, Tag.TAG_DOUBLE)
                ? clamp(tag.getDouble(KEY_BLEEDING), 0.0, MAX_BLEEDING) : 0.0;
        state.internalBleeding = tag.contains(KEY_INTERNAL_BLEEDING, Tag.TAG_DOUBLE)
                ? clamp(tag.getDouble(KEY_INTERNAL_BLEEDING), 0.0, MAX_BLEEDING) : 0.0;
        state.fleshWoundTicks = tag.contains(KEY_FLESH_WOUND_TICKS, Tag.TAG_INT)
                ? Math.max(0, tag.getInt(KEY_FLESH_WOUND_TICKS)) : 0;
        return state;
    }

    public PlayerInjuryState copy() {
        PlayerInjuryState copy = new PlayerInjuryState();
        copy.bleeding         = bleeding;
        copy.internalBleeding = internalBleeding;
        copy.fleshWoundTicks  = fleshWoundTicks;
        return copy;
    }

    public void reset() {
        bleeding         = 0.0;
        internalBleeding = 0.0;
        fleshWoundTicks  = 0;
    }

    private double bleedingFloor() {
        return switch (fleshWoundSeverity()) {
            case 2 -> 2.0;
            case 1 -> 1.0;
            case 0 -> 0.5;
            default -> 0.0;
        };
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
