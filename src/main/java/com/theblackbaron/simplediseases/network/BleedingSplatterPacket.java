package com.theblackbaron.simplediseases.network;

import com.theblackbaron.simplediseases.client.BleedingHudOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record BleedingSplatterPacket(int count) {

    public static void encode(BleedingSplatterPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.count);
    }

    public static BleedingSplatterPacket decode(FriendlyByteBuf buf) {
        return new BleedingSplatterPacket(buf.readVarInt());
    }

    public static void handle(BleedingSplatterPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> BleedingHudOverlay.addSplatter(msg.count));
        ctx.get().setPacketHandled(true);
    }
}
