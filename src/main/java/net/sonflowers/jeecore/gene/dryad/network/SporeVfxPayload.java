package net.sonflowers.jeecore.gene.dryad.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.client.SporeRingRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Delivers a spore visual event or guard-state update to the client.
 */
public record SporeVfxPayload(UUID playerId, Vec3 position, int duration, byte kind) implements CustomPacketPayload {
    public static final byte RELEASE = 0;
    public static final byte GUARD = 1;
    public static final byte END = 2;
    public static final Type<SporeVfxPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "spore_vfx"));
    public static final StreamCodec<ByteBuf, SporeVfxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SporeVfxPayload decode(ByteBuf buffer) {
            UUID player = new UUID(buffer.readLong(), buffer.readLong());
            Vec3 position = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
            return new SporeVfxPayload(player, position, buffer.readInt(), buffer.readByte());
        }

        @Override
        public void encode(ByteBuf buffer, SporeVfxPayload payload) {
            buffer.writeLong(payload.playerId.getMostSignificantBits());
            buffer.writeLong(payload.playerId.getLeastSignificantBits());
            buffer.writeDouble(payload.position.x);
            buffer.writeDouble(payload.position.y);
            buffer.writeDouble(payload.position.z);
            buffer.writeInt(payload.duration);
            buffer.writeByte(payload.kind);
        }
    };

    @Override public Type<SporeVfxPayload> type() { return TYPE; }

    public static void handle(SporeVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().getReceptionSide().isClient()) SporeRingRenderer.receive(payload);
        });
    }
}
