package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.client.GeneSelectionScreen;

/**
 * Clientbound payload. Opens the local gene selection screen.
 */
public record OpenGeneScreenPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenGeneScreenPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "open_gene_screen"));

    public static final StreamCodec<ByteBuf, OpenGeneScreenPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenGeneScreenPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeneScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(GeneSelectionScreen::open);
    }
}
