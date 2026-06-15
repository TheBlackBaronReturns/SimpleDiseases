package com.theblackbaron.simplediseases.status.manager;

import java.util.UUID;

public final class PlayerInfection {
    public UUID infectorId;
    public long lastContact;

    public PlayerInfection(UUID infectorId, long now) {
        this.infectorId  = infectorId;
        this.lastContact = now;
    }
}
