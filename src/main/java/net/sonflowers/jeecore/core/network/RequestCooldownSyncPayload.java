package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.ability.AbilityHandler;

/**
 * Serverbound payload. Requests cooldown synchronization for the sending player.
 */
public record RequestCooldownSyncPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RequestCooldownSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "request_cooldown_sync"));

    public static final StreamCodec<ByteBuf, RequestCooldownSyncPayload> STREAM_CODEC =
            StreamCodec.unit(new RequestCooldownSyncPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(RequestCooldownSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                AbilityHandler.syncCooldowns(serverPlayer);
            }
        });
    }
}
