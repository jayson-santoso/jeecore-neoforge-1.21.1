package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.server.BeamManager;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's beam activation state for client rendering.
 */
public record BeamPayload(UUID playerUUID, boolean beaming) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BeamPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "beam"));

    public static final StreamCodec<ByteBuf, BeamPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BeamPayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            boolean beaming = buf.readBoolean();
            return new BeamPayload(new UUID(msb, lsb), beaming);
        }

        @Override
        public void encode(ByteBuf buf, BeamPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.beaming());
        }
    };

    @Override
    public CustomPacketPayload.Type<BeamPayload> type() {
        return TYPE;
    }

    public static void handle(BeamPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> BeamManager.setBeamingClient(payload.playerUUID(), payload.beaming()));
    }
}
