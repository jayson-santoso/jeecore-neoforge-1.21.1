package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's rage intensity for observer rendering.
 */
public record RageVisualStatePayload(UUID playerUUID, float ragePercent) implements CustomPacketPayload {
    public static final Type<RageVisualStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "rage_visual_state"));

    public static final StreamCodec<ByteBuf, RageVisualStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RageVisualStatePayload decode(ByteBuf buf) {
            UUID playerUUID = new UUID(buf.readLong(), buf.readLong());
            float ragePercent = buf.readFloat();
            return new RageVisualStatePayload(playerUUID, ragePercent);
        }

        @Override
        public void encode(ByteBuf buf, RageVisualStatePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeFloat(payload.ragePercent());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RageVisualStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> RageManager.setRagePercent(payload.playerUUID(), payload.ragePercent()));
    }
}
