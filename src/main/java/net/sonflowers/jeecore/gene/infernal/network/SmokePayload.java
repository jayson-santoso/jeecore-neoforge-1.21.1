package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's smoke visibility state.
 */
public record SmokePayload(UUID playerUUID, boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SmokePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "smoke"));

    public static final StreamCodec<ByteBuf, SmokePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SmokePayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            boolean enabled = buf.readBoolean();
            return new SmokePayload(new UUID(msb, lsb), enabled);
        }

        @Override
        public void encode(ByteBuf buf, SmokePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.enabled());
        }
    };

    @Override
    public CustomPacketPayload.Type<SmokePayload> type() {
        return TYPE;
    }

    public static void handle(SmokePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> SmokeManager.setSmoking(payload.playerUUID(), payload.enabled()));
    }
}
