package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient;

/**
 * Clientbound payload. Mirrors gust charge timing for observers and the casting player.
 */
public record GustChargePayload(boolean charging, long started, int casterId) implements CustomPacketPayload {
    public static final Type<GustChargePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "gust_charge"));
    public static final StreamCodec<ByteBuf, GustChargePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GustChargePayload::charging, ByteBufCodecs.VAR_LONG, GustChargePayload::started,
            ByteBufCodecs.VAR_INT, GustChargePayload::casterId, GustChargePayload::new);
    @Override public Type<GustChargePayload> type() { return TYPE; }
    public static void handle(GustChargePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            net.sonflowers.jeecore.gene.nimbus.client.NimbusGustChargeRenderer.setCharging(payload.casterId, payload.charging, payload.started);
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.getId() == payload.casterId) NimbusGustClient.setCharging(payload.charging, payload.started);
        });
    }
}
