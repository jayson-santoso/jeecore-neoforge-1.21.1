package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.NimbusDodgeRenderer;
import java.util.UUID;

/**
 * Clientbound payload. Triggers a short-lived dodge visual for the owner and tracking observers.
 */
public record NimbusDodgePayload(UUID playerId, Vec3 position, Vec3 motion, float yaw, boolean active)
        implements CustomPacketPayload {
    public static final Type<NimbusDodgePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "nimbus_dodge"));
    public static final StreamCodec<ByteBuf, NimbusDodgePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public NimbusDodgePayload decode(ByteBuf b) {
            return new NimbusDodgePayload(new UUID(b.readLong(), b.readLong()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()),
                    new Vec3(b.readDouble(), b.readDouble(), b.readDouble()), b.readFloat(), b.readBoolean());
        }
        @Override public void encode(ByteBuf b, NimbusDodgePayload p) {
            b.writeLong(p.playerId.getMostSignificantBits()); b.writeLong(p.playerId.getLeastSignificantBits());
            b.writeDouble(p.position.x); b.writeDouble(p.position.y); b.writeDouble(p.position.z);
            b.writeDouble(p.motion.x); b.writeDouble(p.motion.y); b.writeDouble(p.motion.z);
            b.writeFloat(p.yaw); b.writeBoolean(p.active);
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(NimbusDodgePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> NimbusDodgeRenderer.receive(payload));
    }
}
