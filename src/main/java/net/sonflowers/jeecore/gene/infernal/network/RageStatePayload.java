package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.core.client.ClientHudState;

/**
 * Clientbound payload. Mirrors the owner's rage resource and pounce charge count.
 */
public record RageStatePayload(int rageBar, int maxRageBar, int pounceCharges) implements CustomPacketPayload {
    public static final Type<RageStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "rage_state"));

    public static final StreamCodec<ByteBuf, RageStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, RageStatePayload::rageBar,
                    ByteBufCodecs.INT, RageStatePayload::maxRageBar,
                    ByteBufCodecs.INT, RageStatePayload::pounceCharges,
                    RageStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(RageStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerGeneData data = context.player().getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setRageBar(payload.rageBar());
                ClientHudState.setPounceCharges(payload.pounceCharges());
            }
        });
    }
}
