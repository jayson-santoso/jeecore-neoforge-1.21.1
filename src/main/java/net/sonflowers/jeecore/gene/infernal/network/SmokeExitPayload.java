package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.client.SmokeExitRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Triggers a client smoke-exit burst at a player position.
 */
public record SmokeExitPayload(UUID playerUUID, Vec3 position) implements CustomPacketPayload {
    public static final Type<SmokeExitPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "smoke_exit"));

    public static final StreamCodec<ByteBuf, SmokeExitPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SmokeExitPayload decode(ByteBuf buf) {
            return new SmokeExitPayload(
                    new UUID(buf.readLong(), buf.readLong()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())
            );
        }

        @Override
        public void encode(ByteBuf buf, SmokeExitPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeDouble(payload.position().x);
            buf.writeDouble(payload.position().y);
            buf.writeDouble(payload.position().z);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmokeExitPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> SmokeExitRenderer.trigger(payload.playerUUID(), payload.position()));
    }
}