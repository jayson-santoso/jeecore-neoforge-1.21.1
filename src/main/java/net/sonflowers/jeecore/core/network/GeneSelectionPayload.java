package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.core.ability.AbilityHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalPassiveHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalSmokeManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusPassiveHandler;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemOverheatManager;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemPassiveHandler;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;

/**
 * Serverbound payload. Requests a gene selection; the server resolves the identifier and resets
 * incompatible ability state.
 */
public record GeneSelectionPayload(String geneId) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GeneSelectionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "gene_selection"));

    public static final StreamCodec<ByteBuf, GeneSelectionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    GeneSelectionPayload::geneId,
                    GeneSelectionPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                Gene gene = Gene.fromString(payload.geneId);
                if (gene != Gene.NONE) {
                    NimbusPassiveHandler.removePassiveAttributes(player);
                    InfernalPassiveHandler.removePassiveAttributes(player);
                    TerragolemPassiveHandler.removePassiveAttributes(player);
                    InfernalSmokeManager.removeSmokeForm(player);
                    TerragolemReadyUpManager.cancel(player);
                    TerragolemOverheatManager.clearHeat(player);
                    PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
                    data.setIgnitionCharge(0);
                    data.setIgnitionTimer(0);
                    data.setIgnitionCooldownTicks(0);
                    data.setIgnitionQuickTimeActive(false);
                    GeneHelper.setPlayerGene(player, gene);

                    // Apply passive attributes for Nimbus.
                    if (gene == Gene.NIMBUS) {
                        NimbusPassiveHandler.applyPassiveAttributes(player);
                    } else if (gene == Gene.INFERNAL) {
                        InfernalPassiveHandler.applyPassiveAttributes(player);
                        InfernalSmokeManager.sync(player);
                        InfernalChargeManager.sync(player);
                    } else if (gene == Gene.TERRAGOLEM) {
                        TerragolemPassiveHandler.applyPassiveAttributes(player);
                    }
                    PacketDistributor.sendToPlayer(player, new GeneStatePayload(gene.getSerializedName()));
                    AbilityHandler.syncCooldowns(player);
                }
            }
        });
    }
}
