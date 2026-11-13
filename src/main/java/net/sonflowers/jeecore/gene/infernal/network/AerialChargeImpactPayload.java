package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;

import java.util.UUID;

/**
 * Clientbound payload. Triggers client pounce impact effects with distance-scaled local camera
 * feedback.
 */
public record AerialChargeImpactPayload(UUID playerUUID, double x, double y, double z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AerialChargeImpactPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "aerial_charge_impact"));

    public static final StreamCodec<ByteBuf, AerialChargeImpactPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AerialChargeImpactPayload decode(ByteBuf buf) {
            UUID playerUUID = new UUID(buf.readLong(), buf.readLong());
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            return new AerialChargeImpactPayload(playerUUID, x, y, z);
        }

        @Override
        public void encode(ByteBuf buf, AerialChargeImpactPayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
        }
    };

    @Override
    public CustomPacketPayload.Type<AerialChargeImpactPayload> type() {
        return TYPE;
    }

    public static void handle(AerialChargeImpactPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.player != null) {
                    double distanceSq = mc.player.position().distanceToSqr(payload.x(), payload.y(), payload.z());
                    boolean isImpactingPlayer = mc.player.getUUID().equals(payload.playerUUID());
                    // Apply screen shake to players within 20 blocks.
                    if (isImpactingPlayer || distanceSq < 400.0) { // 20^2 = 400.
                        double proximity = 1.0 - Math.sqrt(distanceSq) / 20.0;
                        float intensity = isImpactingPlayer ? 1.0f : (float) Math.max(0.35, proximity);
                        net.sonflowers.jeecore.gene.nimbus.client.WindRenderer.screenShakeIntensity =
                                Math.max(net.sonflowers.jeecore.gene.nimbus.client.WindRenderer.screenShakeIntensity, 1.12f * intensity);
                        net.sonflowers.jeecore.gene.infernal.client.InfernalAerialChargeRenderer.triggerImpactEffects(
                                new net.minecraft.world.phys.Vec3(payload.x(), payload.y(), payload.z()), 1.0f * intensity);
                    }
                }
            }
        });
    }
}
