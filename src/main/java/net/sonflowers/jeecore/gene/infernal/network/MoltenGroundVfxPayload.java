package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.client.MoltenGroundRenderer;

/**
 * Clientbound payload. Adds a transient client molten-ground decal at the supplied position.
 */
public record MoltenGroundVfxPayload(double x, double y, double z) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MoltenGroundVfxPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "molten_ground_vfx"));

    public static final StreamCodec<ByteBuf, MoltenGroundVfxPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MoltenGroundVfxPayload decode(ByteBuf buf) {
            return new MoltenGroundVfxPayload(
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble()
            );
        }

        @Override
        public void encode(ByteBuf buf, MoltenGroundVfxPayload payload) {
            buf.writeDouble(payload.x());
            buf.writeDouble(payload.y());
            buf.writeDouble(payload.z());
        }
    };

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MoltenGroundVfxPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {

            MoltenGroundRenderer.addMoltenGround(payload.x(), payload.y(), payload.z());
        });
    }
}
