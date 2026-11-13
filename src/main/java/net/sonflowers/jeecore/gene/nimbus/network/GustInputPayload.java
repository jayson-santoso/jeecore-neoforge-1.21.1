package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusGustManager;

/**
 * Serverbound payload. Forwards gust input to the server manager for the sending player.
 */
public record GustInputPayload(byte action) implements CustomPacketPayload {
    public static final byte PRESS = 0, RELEASE = 1, CANCEL = 2, HOLD = 3;
    public static final Type<GustInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "gust_input"));
    public static final StreamCodec<ByteBuf, GustInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, GustInputPayload::action, GustInputPayload::new);
    @Override public Type<GustInputPayload> type() { return TYPE; }
    public static void handle(GustInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) NimbusGustManager.handleInput(player, payload.action);
        });
    }
}
