package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusWindrunnerManager;

/**
 * Serverbound payload. Reports an empty swing so the server can spend only the sending player's
 * momentum.
 */
public record WindrunnerAttackPayload() implements CustomPacketPayload {
    public static final WindrunnerAttackPayload INSTANCE = new WindrunnerAttackPayload();
    public static final Type<WindrunnerAttackPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "windrunner_attack"));
    public static final StreamCodec<ByteBuf, WindrunnerAttackPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(WindrunnerAttackPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NimbusWindrunnerManager.attack(context.player()));
    }
}
