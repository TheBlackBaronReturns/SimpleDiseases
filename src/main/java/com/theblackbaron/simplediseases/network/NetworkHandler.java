package com.theblackbaron.simplediseases.network;

import com.theblackbaron.simplediseases.SimpleDiseases;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class NetworkHandler {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(SimpleDiseases.MOD_ID, "main"),
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals
    );

    private NetworkHandler() {}

    public static void register() {
        CHANNEL.registerMessage(0,
                BleedingSplatterPacket.class,
                BleedingSplatterPacket::encode,
                BleedingSplatterPacket::decode,
                BleedingSplatterPacket::handle);
        CHANNEL.registerMessage(1,
                DiseaseStateSyncPacket.class,
                DiseaseStateSyncPacket::encode,
                DiseaseStateSyncPacket::decode,
                DiseaseStateSyncPacket::handle);
    }

    public static void sendBleedingSplatter(ServerPlayer player, int count) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new BleedingSplatterPacket(count));
    }

    public static void sendDiseaseStateSync(ServerPlayer player, net.minecraft.nbt.CompoundTag root) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new DiseaseStateSyncPacket(root.copy()));
    }
}
