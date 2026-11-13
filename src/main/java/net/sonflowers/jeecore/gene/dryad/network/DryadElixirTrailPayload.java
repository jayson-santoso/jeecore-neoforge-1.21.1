package net.sonflowers.jeecore.gene.dryad.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.client.DryadElixirTrailRenderer;

/**
 * Clientbound payload. Starts client trail tracking for an elixir entity ID.
 */
public record DryadElixirTrailPayload(int entityId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DryadElixirTrailPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "dryad_elixir_trail"));

    public static final StreamCodec<ByteBuf, DryadElixirTrailPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    DryadElixirTrailPayload::entityId,
                    DryadElixirTrailPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DryadElixirTrailPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> DryadElixirTrailRenderer.track(payload.entityId()));
    }
}
