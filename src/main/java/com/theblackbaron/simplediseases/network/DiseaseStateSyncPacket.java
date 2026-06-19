package com.theblackbaron.simplediseases.network;

import com.theblackbaron.simplediseases.client.ClientDiseaseState;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DiseaseStateSyncPacket(CompoundTag root) {

    public static void encode(DiseaseStateSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.root);
    }

    public static DiseaseStateSyncPacket decode(FriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new DiseaseStateSyncPacket(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(DiseaseStateSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientDiseaseState.applySync(msg.root));
        ctx.get().setPacketHandled(true);
    }
}
