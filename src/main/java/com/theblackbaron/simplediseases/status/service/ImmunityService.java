package com.theblackbaron.simplediseases.status.service;

import com.theblackbaron.simplediseases.status.component.ImmunityComponent;

/**
 * Post-recovery immunity windows, keyed per disease via {@link ImmunityComponent}. Category-agnostic
 * so any family that grants immunity after recovery reuses it.
 */
public final class ImmunityService {
    private ImmunityService() {}

    public static boolean isImmune(ImmunityComponent immunity, long gameTime) {
        return immunity != null && immunity.immunityUntil > gameTime;
    }

    public static void grant(ImmunityComponent immunity, long gameTime, long ticks) {
        immunity.immunityUntil = gameTime + ticks;
    }
}
