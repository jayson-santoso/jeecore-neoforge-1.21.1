package net.sonflowers.jeecore.gene.dryad.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.client.HealRenderer;
import net.sonflowers.jeecore.gene.dryad.server.HealManager;

/**
 * Clientbound payload. Adds or removes a client healing zone; BlockPos.ZERO clears all zones when
 * clear is true.
 */
public record HealPayload(BlockPos pos, long spawnTime, boolean clear) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<HealPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "heal"));

    public static final StreamCodec<ByteBuf, HealPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HealPayload decode(ByteBuf buf) {
            long packed = buf.readLong();
            long spawnTime = buf.readLong();
            boolean clear = buf.readBoolean();
            return new HealPayload(BlockPos.of(packed), spawnTime, clear);
        }

        @Override
        public void encode(ByteBuf buf, HealPayload payload) {
            buf.writeLong(payload.pos().asLong());
            buf.writeLong(payload.spawnTime());
            buf.writeBoolean(payload.clear());
        }
    };

    @Override
    public CustomPacketPayload.Type<HealPayload> type() {
        return TYPE;
    }

    public static void handle(HealPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (payload.clear()) {
                long gameTime = Minecraft.getInstance().level != null ? Minecraft.getInstance().level.getGameTime() : 0L;
                if (payload.pos().equals(BlockPos.ZERO)) {
                    HealRenderer.startEndAnimations(HealManager.getZones(), gameTime);
                    HealManager.getZones().clear();
                } else {
                    HealRenderer.startEndAnimation(payload.pos(), gameTime);
                    HealManager.getZones().remove(payload.pos());
                }
            } else {
                HealManager.getZones().put(payload.pos(), payload.spawnTime());
                HealRenderer.cancelEndAnimation(payload.pos());
            }
        });
    }
}
