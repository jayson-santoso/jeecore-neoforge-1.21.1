package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;

/**
 * Clientbound payload. Mirrors the owner's heat bar and primary lock.
 */
public record TerragolemOverheatPayload(int overheatBar, int maxOverheatBar, boolean primaryLocked) implements CustomPacketPayload {
    public static final Type<TerragolemOverheatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_overheat"));

    public static final StreamCodec<ByteBuf, TerragolemOverheatPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT, TerragolemOverheatPayload::overheatBar,
                    ByteBufCodecs.INT, TerragolemOverheatPayload::maxOverheatBar,
                    ByteBufCodecs.BOOL, TerragolemOverheatPayload::primaryLocked,
                    TerragolemOverheatPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TerragolemOverheatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerGeneData data = context.player().getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setOverheatBar(payload.overheatBar());
                data.setOverheatPrimaryLocked(payload.primaryLocked());
            }
        });
    }
}
