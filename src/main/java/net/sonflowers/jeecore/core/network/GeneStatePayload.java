package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;

/**
 * Clientbound payload. Mirrors the local player's selected gene.
 */
public record GeneStatePayload(String geneId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GeneStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "gene_state"));

    public static final StreamCodec<ByteBuf, GeneStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    GeneStatePayload::geneId,
                    GeneStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                context.player()
                        .getData(GeneDataAttachment.PLAYER_GENE_DATA)
                        .setGene(Gene.fromString(payload.geneId()));
            }
        });
    }
}
