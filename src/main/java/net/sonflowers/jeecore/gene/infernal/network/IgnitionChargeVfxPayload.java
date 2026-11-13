package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.client.InfernalChargeVfxRenderer;

/**
 * Clientbound payload. Triggers client ignition priming or detonation effects.
 */
public record IgnitionChargeVfxPayload(int entityId, Vec3 pos, boolean detonating, boolean timeout) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IgnitionChargeVfxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "ignition_charge_vfx"));

    public static final StreamCodec<ByteBuf, IgnitionChargeVfxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IgnitionChargeVfxPayload decode(ByteBuf buf) {
            return new IgnitionChargeVfxPayload(
                    buf.readInt(),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    buf.readBoolean(),
                    buf.readBoolean()
            );
        }

        @Override
        public void encode(ByteBuf buf, IgnitionChargeVfxPayload payload) {
            buf.writeInt(payload.entityId());
            buf.writeDouble(payload.pos().x);
            buf.writeDouble(payload.pos().y);
            buf.writeDouble(payload.pos().z);
            buf.writeBoolean(payload.detonating());
            buf.writeBoolean(payload.timeout());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IgnitionChargeVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.detonating()) {
                InfernalChargeVfxRenderer.detonate(payload.entityId(), payload.pos(), payload.timeout());
            } else {
                InfernalChargeVfxRenderer.prime(payload.entityId(), payload.pos());
            }
        });
    }
}
