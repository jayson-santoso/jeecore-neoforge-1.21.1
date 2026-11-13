package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.server.CloudManager;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's cloud visibility state.
 */
public record CloudPayload(UUID playerUUID, boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CloudPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "cloud"));

    public static final StreamCodec<ByteBuf, CloudPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public CloudPayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            boolean enabled = buf.readBoolean();
            return new CloudPayload(new UUID(msb, lsb), enabled);
        }

        @Override
        public void encode(ByteBuf buf, CloudPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.enabled());
        }
    };

    @Override
    public CustomPacketPayload.Type<CloudPayload> type() {
        return TYPE;
    }

    public static void handle(CloudPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> CloudManager.setClouded(payload.playerUUID(), payload.enabled()));
    }
}
