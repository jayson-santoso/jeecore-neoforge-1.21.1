package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.client.InfernalAerialChargeRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's pounce activation state for client effects.
 */
public record InfernalAerialChargePayload(UUID playerUUID, boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<InfernalAerialChargePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_aerial_charge"));

    public static final StreamCodec<ByteBuf, InfernalAerialChargePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InfernalAerialChargePayload decode(ByteBuf buf) {
            UUID playerUUID = new UUID(buf.readLong(), buf.readLong());
            boolean active = buf.readBoolean();
            return new InfernalAerialChargePayload(playerUUID, active);
        }

        @Override
        public void encode(ByteBuf buf, InfernalAerialChargePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.active());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InfernalAerialChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> InfernalAerialChargeRenderer.setPouncing(payload.playerUUID(), payload.active()));
    }
}
