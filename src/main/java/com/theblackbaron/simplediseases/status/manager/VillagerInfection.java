package com.theblackbaron.simplediseases.status.manager;

import java.util.UUID;

public final class VillagerInfection {
    public int  exposure;
    public int  generation;
    public int  transmissions;
    public UUID infectorId;
    public long lastContact;
    public long immunityUntil; // 0 until infected; then now + COLD_TICKS + IMMUNITY_TICKS

    public VillagerInfection(UUID infectorId, long now) {
        this.infectorId  = infectorId;
        this.lastContact = now;
    }
}
