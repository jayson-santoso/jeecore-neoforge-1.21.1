package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;

/**
 * Serverbound payload. Reports jump input to the server ready-up manager.
 */
public record TerragolemJumpPayload() implements CustomPacketPayload {
    public static final Type<TerragolemJumpPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_jump"));
    public static final StreamCodec<ByteBuf, TerragolemJumpPayload> STREAM_CODEC = StreamCodec.unit(new TerragolemJumpPayload());

    @Override
    public Type<TerragolemJumpPayload> type() { return TYPE; }

    public static void handle(TerragolemJumpPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) TerragolemReadyUpManager.recordPlayerJump(player);
        });
    }
}
