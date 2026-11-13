package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;

/**
 * Clientbound payload. Mirrors the owner's glide toggle and resource.
 */
public record GlideStatePayload(boolean toggled, int resource) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlideStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "glide_state"));

    public static final StreamCodec<ByteBuf, GlideStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    GlideStatePayload::toggled,
                    ByteBufCodecs.INT,
                    GlideStatePayload::resource,
                    GlideStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlideStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerGeneData data = context.player().getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setGene(Gene.NIMBUS);
                data.setGlideToggled(payload.toggled());
                data.setGlideResource(payload.resource());
            }
        });
    }
}
