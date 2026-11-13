package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;

/**
 * Clientbound payload. Mirrors owner-only momentum and stage state, suppressing transition
 * feedback on initial synchronization.
 */
public record WindrunnerStatePayload(float momentum, int stage, boolean decaying, int transition) implements CustomPacketPayload {
    public static final Type<WindrunnerStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "windrunner"));
    public static final StreamCodec<ByteBuf, WindrunnerStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, WindrunnerStatePayload::momentum,
            ByteBufCodecs.INT, WindrunnerStatePayload::stage,
            ByteBufCodecs.BOOL, WindrunnerStatePayload::decaying,
            ByteBufCodecs.INT, WindrunnerStatePayload::transition, WindrunnerStatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(WindrunnerStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;
            var state = player.getData(GeneDataAttachment.WINDRUNNER);
            state.clientMomentum = Math.clamp(payload.momentum, 0, 100);
            state.clientStage = Math.clamp(payload.stage, 0, 3);
            state.clientDecaying = payload.decaying;
            if (state.clientInitialized && payload.transition != 0) {
                state.transition = payload.transition;
                state.transitionTick = player.level().getGameTime();
                if (payload.transition > 0) {
                    state.promotionTick = state.transitionTick;
                    state.promotionStage = state.clientStage;
                }
            }
            state.clientInitialized = true;
        });
    }
}
