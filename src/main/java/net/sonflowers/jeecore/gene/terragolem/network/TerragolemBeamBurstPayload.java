package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemBurstRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Triggers a client burst beam and its optional impact effects.
 */
public record TerragolemBeamBurstPayload(UUID playerUUID, int segments, double endX, double endY, double endZ,
                                         boolean impact) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemBeamBurstPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_beam_burst"));

    public static final StreamCodec<ByteBuf, TerragolemBeamBurstPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerragolemBeamBurstPayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            int segments = buf.readInt();
            double endX = buf.readDouble();
            double endY = buf.readDouble();
            double endZ = buf.readDouble();
            boolean impact = buf.readBoolean();
            return new TerragolemBeamBurstPayload(new UUID(msb, lsb), segments, endX, endY, endZ, impact);
        }

        @Override
        public void encode(ByteBuf buf, TerragolemBeamBurstPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeInt(payload.segments());
            buf.writeDouble(payload.endX());
            buf.writeDouble(payload.endY());
            buf.writeDouble(payload.endZ());
            buf.writeBoolean(payload.impact());
        }
    };

    @Override
    public CustomPacketPayload.Type<TerragolemBeamBurstPayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemBeamBurstPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                TerragolemBurstRenderer.addBurst(payload.playerUUID(), payload.segments(),
                        payload.endX(), payload.endY(), payload.endZ(), payload.impact());
            }
        });
    }
}
