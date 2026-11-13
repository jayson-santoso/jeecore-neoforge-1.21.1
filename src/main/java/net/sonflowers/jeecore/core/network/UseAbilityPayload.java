package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.ability.AbilityHandler;

/**
 * Serverbound payload. Requests the sending player's primary or secondary ability through server
 * ability checks.
 */
public record UseAbilityPayload(boolean primary) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "use_ability"));

    public static final StreamCodec<ByteBuf, UseAbilityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    UseAbilityPayload::primary,
                    UseAbilityPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UseAbilityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.primary) {
                    AbilityHandler.usePrimaryAbility(player);
                } else {
                    AbilityHandler.useSecondaryAbility(player);
                }
            }
        });
    }
}
