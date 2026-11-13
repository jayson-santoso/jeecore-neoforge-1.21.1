package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

/**
 * Clientbound payload. Triggers local timeout-stun camera and flash feedback.
 */
public record TerragolemBigStunPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemBigStunPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_big_stun"));

    public static final StreamCodec<ByteBuf, TerragolemBigStunPayload> STREAM_CODEC =
            StreamCodec.unit(new TerragolemBigStunPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemBigStunPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                TerragolemReadyUpRenderer.triggerBigStun();
            }
        });
    }
}
