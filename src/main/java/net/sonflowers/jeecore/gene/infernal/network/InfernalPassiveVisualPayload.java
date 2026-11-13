package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;

import java.util.UUID;

/**
 * Clientbound payload. Reserves the Infernal passive visual message; the current handler
 * intentionally performs no work.
 */
public record InfernalPassiveVisualPayload(UUID playerUUID, boolean active) implements CustomPacketPayload {
    public static final Type<InfernalPassiveVisualPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_passive_visual"));

    public static final StreamCodec<ByteBuf, InfernalPassiveVisualPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public InfernalPassiveVisualPayload decode(ByteBuf buf) {
            return new InfernalPassiveVisualPayload(new UUID(buf.readLong(), buf.readLong()), buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, InfernalPassiveVisualPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeBoolean(payload.active());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(InfernalPassiveVisualPayload payload, IPayloadContext context) {
        // This reserved message intentionally has no visual effect.
    }
}
