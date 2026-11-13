package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.WindRenderer;

/**
 * Clientbound payload. Starts client gust rendering and release feedback using server-supplied
 * cone parameters.
 */
public record WindPayload(Vec3 pos, Vec3 direction, float radius, float charge, int casterId) implements CustomPacketPayload {
    public static final Type<WindPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "wind"));
    public static final StreamCodec<ByteBuf, WindPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public WindPayload decode(ByteBuf buf) {
            return new WindPayload(new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readInt());
        }
        @Override public void encode(ByteBuf buf, WindPayload p) {
            buf.writeDouble(p.pos.x); buf.writeDouble(p.pos.y); buf.writeDouble(p.pos.z);
            buf.writeDouble(p.direction.x); buf.writeDouble(p.direction.y); buf.writeDouble(p.direction.z);
            buf.writeFloat(p.radius); buf.writeFloat(p.charge);
            buf.writeInt(p.casterId);
        }
    };
    @Override public Type<WindPayload> type() { return TYPE; }
    public static void handle(WindPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            WindRenderer.trigger(payload.pos, payload.direction, payload.radius, payload.charge);
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.getId() == payload.casterId) {
                net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient.onRelease(payload.charge);
            }
        });
    }
}
