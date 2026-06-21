package com.theblackbaron.simplediseases.status.def;

/** Shared stochastic momentum worsening: chance rises with each successful tier increase. */
public final class WorseningRoll {
    public static final float BASE     = 0.30f;
    public static final float MOMENTUM = 0.25f;

    private WorseningRoll() {}

    public static float chance(int worsenings) {
        return Math.min(1.0f, BASE + MOMENTUM * worsenings);
    }
}
