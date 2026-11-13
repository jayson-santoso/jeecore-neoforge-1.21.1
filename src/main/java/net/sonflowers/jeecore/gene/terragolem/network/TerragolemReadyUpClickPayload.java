package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;

/**
 * Serverbound payload. Forwards mouse button and press state to the server ready-up manager.
 */
public record TerragolemReadyUpClickPayload(int button, boolean down) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemReadyUpClickPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_ready_up_click"));

    public static final StreamCodec<ByteBuf, TerragolemReadyUpClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    TerragolemReadyUpClickPayload::button,
                    ByteBufCodecs.BOOL,
                    TerragolemReadyUpClickPayload::down,
                    TerragolemReadyUpClickPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemReadyUpClickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                TerragolemReadyUpManager.handleClick(player, payload.button(), payload.down());
            }
        });
    }
}
