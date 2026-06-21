package com.theblackbaron.simplediseases.network;

import com.theblackbaron.simplediseases.client.ClientDebugOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public record DebugOverlayPacket(int updateMask, List<String> viralLines, List<String> bacterialLines) {

    public static final int UPDATE_VIRAL = 1;
    public static final int UPDATE_BACTERIAL = 2;
    public static final int UPDATE_ALL = UPDATE_VIRAL | UPDATE_BACTERIAL;

    public static void encode(DebugOverlayPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.updateMask);
        writeLines(buf, msg.viralLines);
        writeLines(buf, msg.bacterialLines);
    }

    public static DebugOverlayPacket decode(FriendlyByteBuf buf) {
        int mask = buf.readVarInt();
        return new DebugOverlayPacket(mask, readLines(buf), readLines(buf));
    }

    public static void handle(DebugOverlayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.updateMask == 0) {
                ClientDebugOverlay.clearAll();
            } else {
                ClientDebugOverlay.update(msg.updateMask, msg.viralLines, msg.bacterialLines);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void writeLines(FriendlyByteBuf buf, List<String> lines) {
        buf.writeVarInt(lines.size());
        for (String line : lines) {
            buf.writeUtf(line, 2048);
        }
    }

    private static List<String> readLines(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<String> lines = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            lines.add(buf.readUtf(2048));
        }
        return lines;
    }
}
