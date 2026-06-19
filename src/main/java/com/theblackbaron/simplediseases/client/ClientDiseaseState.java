package com.theblackbaron.simplediseases.client;

import com.theblackbaron.simplediseases.SimpleDiseases;
import com.theblackbaron.simplediseases.status.manager.PlayerDiseaseState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/** Client-side mirror of synced player disease NBT for tooltip rendering. */
public final class ClientDiseaseState {
    private static CompoundTag syncedRoot = new CompoundTag();

    private ClientDiseaseState() {}

    public static void applySync(CompoundTag root) {
        syncedRoot = root == null ? new CompoundTag() : root.copy();
    }

    public static PlayerDiseaseState forPlayer(@Nullable Player player) {
        if (player == null) return PlayerDiseaseState.loadFromNbt(new CompoundTag());

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            ServerPlayer sp = server.getPlayerList().getPlayer(player.getUUID());
            if (sp != null) {
                return PlayerDiseaseState.loadFromNbt(
                        sp.getPersistentData().getCompound(SimpleDiseases.MOD_ID));
            }
        }

        if (!syncedRoot.isEmpty()) {
            return PlayerDiseaseState.loadFromNbt(syncedRoot);
        }

        return PlayerDiseaseState.loadFromNbt(
                player.getPersistentData().getCompound(SimpleDiseases.MOD_ID));
    }
}
