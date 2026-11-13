package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.client.ClientHudState;

/**
 * Clientbound payload. Updates one local HUD cooldown; durations are in game ticks.
 */
public record CooldownStatePayload(boolean primary, int remainingTicks, int maxTicks) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CooldownStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "cooldown_state"));

    public static final StreamCodec<ByteBuf, CooldownStatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    CooldownStatePayload::primary,
                    ByteBufCodecs.INT,
                    CooldownStatePayload::remainingTicks,
                    ByteBufCodecs.INT,
                    CooldownStatePayload::maxTicks,
                    CooldownStatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CooldownStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            double gameTime = mc.level != null ? mc.level.getGameTime() : 0.0;
            ClientHudState.setCooldown(payload.primary(), payload.remainingTicks(), payload.maxTicks(), gameTime);
        });
    }
}
