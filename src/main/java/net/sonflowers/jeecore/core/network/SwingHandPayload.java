package net.sonflowers.jeecore.core.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;

/**
 * Clientbound payload. Animates the local main hand after a server-accepted action.
 */
public record SwingHandPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SwingHandPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "swing_hand"));

    public static final StreamCodec<ByteBuf, SwingHandPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SwingHandPayload decode(ByteBuf buf) {
            return new SwingHandPayload();
        }

        @Override
        public void encode(ByteBuf buf, SwingHandPayload payload) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwingHandPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.swing(InteractionHand.MAIN_HAND);
            }
        });
    }
}
