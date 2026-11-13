package net.sonflowers.jeecore.gene.dryad.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.client.RootSnareRenderer;

/**
 * Clientbound payload. Starts client snare debris using the target dimensions and a duration in
 * game ticks.
 */
public record RootSnareVfxPayload(int entityId, int durationTicks, float width, float height) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RootSnareVfxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "root_snare_vfx"));

    public static final net.minecraft.network.codec.StreamCodec<ByteBuf, RootSnareVfxPayload> STREAM_CODEC = new net.minecraft.network.codec.StreamCodec<>() {
        @Override
        public RootSnareVfxPayload decode(ByteBuf buf) {
            return new RootSnareVfxPayload(buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(ByteBuf buf, RootSnareVfxPayload payload) {
            buf.writeInt(payload.entityId());
            buf.writeInt(payload.durationTicks());
            buf.writeFloat(payload.width());
            buf.writeFloat(payload.height());
        }
    };

    @Override
    public CustomPacketPayload.Type<RootSnareVfxPayload> type() {
        return TYPE;
    }

    public static void handle(RootSnareVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.flow().getReceptionSide().isClient()) {
                RootSnareRenderer.start(payload.entityId(), payload.durationTicks(), payload.width(), payload.height());
            }
        });
    }
}
