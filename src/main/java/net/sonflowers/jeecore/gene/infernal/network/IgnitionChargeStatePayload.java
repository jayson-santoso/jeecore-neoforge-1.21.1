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
 * Clientbound payload. Mirrors the owner's ignition resource, timer, cooldown, and quick-time
 * state.
 */
public record IgnitionChargeStatePayload(
        boolean enabled,
        int charge,
        int timer,
        int cooldownTicks,
        boolean quickTimeActive,
        int quickTimeZoneStart,
        int quickTimeZoneWidth
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IgnitionChargeStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "ignition_charge_state"));

    public static final StreamCodec<ByteBuf, IgnitionChargeStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IgnitionChargeStatePayload decode(ByteBuf buf) {
            return new IgnitionChargeStatePayload(
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, IgnitionChargeStatePayload payload) {
            ByteBufCodecs.BOOL.encode(buf, payload.enabled());
            ByteBufCodecs.INT.encode(buf, payload.charge());
            ByteBufCodecs.INT.encode(buf, payload.timer());
            ByteBufCodecs.INT.encode(buf, payload.cooldownTicks());
            ByteBufCodecs.BOOL.encode(buf, payload.quickTimeActive());
            ByteBufCodecs.INT.encode(buf, payload.quickTimeZoneStart());
            ByteBufCodecs.INT.encode(buf, payload.quickTimeZoneWidth());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IgnitionChargeStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() != null) {
                PlayerGeneData data = context.player().getData(GeneDataAttachment.PLAYER_GENE_DATA);
                data.setGene(Gene.INFERNAL);
                data.setIgnitionChargeEnabled(payload.enabled());
                data.setIgnitionCharge(payload.charge());
                data.setIgnitionTimer(payload.timer());
                data.setIgnitionCooldownTicks(payload.cooldownTicks());
                data.setIgnitionQuickTimeActive(payload.quickTimeActive());
                data.setIgnitionQuickTimeZoneStart(payload.quickTimeZoneStart());
                data.setIgnitionQuickTimeZoneWidth(payload.quickTimeZoneWidth());
            }
        });
    }
}
