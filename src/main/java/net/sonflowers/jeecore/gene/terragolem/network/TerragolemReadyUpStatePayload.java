package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's ready-up activation and start time for client rendering.
 */
public record TerragolemReadyUpStatePayload(UUID playerUUID, boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemReadyUpStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_ready_up_state"));

    public static final StreamCodec<ByteBuf, TerragolemReadyUpStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerragolemReadyUpStatePayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            boolean active = buf.readBoolean();
            return new TerragolemReadyUpStatePayload(new UUID(msb, lsb), active);
        }

        @Override
        public void encode(ByteBuf buf, TerragolemReadyUpStatePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.active());
        }
    };

    @Override
    public CustomPacketPayload.Type<TerragolemReadyUpStatePayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemReadyUpStatePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                TerragolemReadyUpRenderer.setReadyUp(payload.playerUUID(), payload.active());
            }
        });
    }
}
