package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;

import java.util.UUID;

/**
 * Clientbound payload. Clears a player's client rage state.
 */
public record RageEndPayload(UUID playerUUID) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RageEndPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "rage_end"));

    public static final StreamCodec<ByteBuf, RageEndPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RageEndPayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            return new RageEndPayload(new UUID(msb, lsb));
        }

        @Override
        public void encode(ByteBuf buf, RageEndPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
        }
    };

    @Override
    public CustomPacketPayload.Type<RageEndPayload> type() {
        return TYPE;
    }

    public static void handle(RageEndPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {

            RageManager.endRage(payload.playerUUID());
        });
    }
}
