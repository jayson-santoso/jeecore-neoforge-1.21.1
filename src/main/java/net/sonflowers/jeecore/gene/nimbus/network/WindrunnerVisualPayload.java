package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.NimbusWindrunnerRenderer;
import java.util.UUID;

/**
 * Clientbound payload. Shares stage and promotion visuals with observers without exposing the
 * owner-only momentum HUD.
 */
public record WindrunnerVisualPayload(UUID playerId, Vec3 position, Vec3 motion, int stage, boolean promotion)
        implements CustomPacketPayload {
    public static final Type<WindrunnerVisualPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "windrunner_visual"));
    public static final StreamCodec<ByteBuf, WindrunnerVisualPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public WindrunnerVisualPayload decode(ByteBuf b) {
            return new WindrunnerVisualPayload(new UUID(b.readLong(), b.readLong()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readInt(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, WindrunnerVisualPayload p) {
            b.writeLong(p.playerId.getMostSignificantBits()); b.writeLong(p.playerId.getLeastSignificantBits());
            b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);
            b.writeDouble(p.motion.x); b.writeDouble(p.motion.y); b.writeDouble(p.motion.z);
            b.writeInt(p.stage); b.writeBoolean(p.promotion);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(WindrunnerVisualPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NimbusWindrunnerRenderer.receive(payload));
    }
}
