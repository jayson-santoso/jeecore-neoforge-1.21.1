package net.sonflowers.jeecore.gene.infernal.network;

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
 * Clientbound payload. Mirrors the owner's smoke mode, resource, and cooldown in game ticks.
 */
public record SmokeFormStatePayload(boolean active, int resource, int cooldownTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SmokeFormStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "smoke_form_state"));

    public static final StreamCodec<ByteBuf, SmokeFormStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    SmokeFormStatePayload::active,
                    ByteBufCodecs.INT,
                    SmokeFormStatePayload::resource,
                    ByteBufCodecs.INT,
                    SmokeFormStatePayload::cooldownTicks,
                    SmokeFormStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SmokeFormStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerGeneData data = context.player().getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setGene(Gene.INFERNAL);
                data.setSmokeFormActive(payload.active());
                data.setSmokeResource(payload.resource());
                data.setSmokeCooldownTicks(payload.cooldownTicks());
            }
        });
    }
}
