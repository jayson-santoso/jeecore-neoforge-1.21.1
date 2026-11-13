package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.GlideBoostRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Starts client glide-boost feedback from the server-supplied origin and
 * direction.
 */
public record GlideBoostEffectPayload(UUID playerUUID, Vec3 origin, Vec3 direction) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<GlideBoostEffectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "glide_boost_effect"));

    public static final StreamCodec<ByteBuf, GlideBoostEffectPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GlideBoostEffectPayload decode(ByteBuf buf) {
            return new GlideBoostEffectPayload(new UUID(buf.readLong(), buf.readLong()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()));
        }

        @Override
        public void encode(ByteBuf buf, GlideBoostEffectPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeDouble(payload.origin.x); buf.writeDouble(payload.origin.y); buf.writeDouble(payload.origin.z);
            buf.writeDouble(payload.direction.x); buf.writeDouble(payload.direction.y); buf.writeDouble(payload.direction.z);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GlideBoostEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;

            var player = mc.level.getPlayerByUUID(payload.playerUUID());
            if (player != null) {
                GlideBoostRenderer.spawnBurst(player, payload.origin, payload.direction);
            }
        });
    }
}
