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
 * Clientbound payload. Mirrors a player's held flare and frozen charge for client rendering.
 */
public record TerragolemFlareHoldPayload(UUID playerUUID, boolean held, float charge) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemFlareHoldPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_flare_hold"));

    public static final StreamCodec<ByteBuf, TerragolemFlareHoldPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public TerragolemFlareHoldPayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            boolean held = buf.readBoolean();
            float charge = buf.readFloat();
            return new TerragolemFlareHoldPayload(new UUID(msb, lsb), held, charge);
        }

        @Override
        public void encode(ByteBuf buf, TerragolemFlareHoldPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.held());
            buf.writeFloat(payload.charge());
        }
    };

    @Override
    public CustomPacketPayload.Type<TerragolemFlareHoldPayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemFlareHoldPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                TerragolemReadyUpRenderer.setHeld(payload.playerUUID(), payload.held(), payload.charge());
            }
        });
    }
}
