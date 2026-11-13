package net.sonflowers.jeecore.gene.infernal.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.client.InfernalChargeVfxRenderer;

import java.util.UUID;

/**
 * Clientbound payload. Mirrors a player's armed-charge timer for observer visuals.
 */
public record IgnitionChargeVisualStatePayload(UUID playerUUID, boolean armed, int timer, int maxTimer) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<IgnitionChargeVisualStatePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "ignition_charge_visual_state"));

    public static final StreamCodec<ByteBuf, IgnitionChargeVisualStatePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public IgnitionChargeVisualStatePayload decode(ByteBuf buf) {
            UUID uuid = new UUID(buf.readLong(), buf.readLong());
            return new IgnitionChargeVisualStatePayload(
                    uuid,
                    ByteBufCodecs.BOOL.decode(buf),
                    ByteBufCodecs.INT.decode(buf),
                    ByteBufCodecs.INT.decode(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, IgnitionChargeVisualStatePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            ByteBufCodecs.BOOL.encode(buf, payload.armed());
            ByteBufCodecs.INT.encode(buf, payload.timer());
            ByteBufCodecs.INT.encode(buf, payload.maxTimer());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(IgnitionChargeVisualStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> InfernalChargeVfxRenderer.setRemoteArmedState(
                payload.playerUUID(),
                payload.armed(),
                payload.timer(),
                payload.maxTimer()
        ));
    }
}
