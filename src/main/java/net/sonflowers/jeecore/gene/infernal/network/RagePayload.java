package net.sonflowers.jeecore.gene.infernal.network;

import net.sonflowers.jeecore.gene.infernal.client.RageRenderer;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;

import java.util.UUID;

/**
 * Clientbound payload. Starts client rage timing and activation visuals for a player.
 */
public record RagePayload(UUID playerUUID, int duration) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<RagePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "rage"));

    public static final StreamCodec<ByteBuf, RagePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public RagePayload decode(ByteBuf buf) {
            long msb = buf.readLong();
            long lsb = buf.readLong();
            int duration = buf.readInt();
            return new RagePayload(new UUID(msb, lsb), duration);
        }

        @Override
        public void encode(ByteBuf buf, RagePayload payload) {
            buf.writeLong(payload.playerUUID().getMostSignificantBits());
            buf.writeLong(payload.playerUUID().getLeastSignificantBits());
            buf.writeInt(payload.duration());
        }
    };

    @Override
    public CustomPacketPayload.Type<RagePayload> type() {
        return TYPE;
    }

    public static void handle(RagePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            RageManager.startRage(payload.playerUUID(), payload.duration());

            if (ctx.flow().getReceptionSide().isClient()) {
                var mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.level != null) {
                    var target = mc.level.getPlayerByUUID(payload.playerUUID());
                    if (target != null) {
                        net.sonflowers.jeecore.gene.infernal.client.RageRenderer.triggerExplosion(target);
                    }
                }
            }
        });
    }
}
