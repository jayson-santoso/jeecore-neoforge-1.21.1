package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemMalfunctionRenderer;
import java.util.UUID;

/**
 * Clientbound payload. Updates client malfunction state or triggers an overheat burst.
 */
public record TerragolemMalfunctionPayload(UUID player, float heat, int timeoutTicks, int burstKind) implements CustomPacketPayload {
    public TerragolemMalfunctionPayload(UUID player, float heat, int timeoutTicks) {
        this(player, heat, timeoutTicks, 0);
    }
    public static final Type<TerragolemMalfunctionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_malfunction"));
    public static final StreamCodec<ByteBuf, TerragolemMalfunctionPayload> STREAM_CODEC = new StreamCodec<>() {
        public TerragolemMalfunctionPayload decode(ByteBuf buf) {
            return new TerragolemMalfunctionPayload(new UUID(buf.readLong(), buf.readLong()), buf.readFloat(), buf.readInt(), buf.readInt());
        }
        public void encode(ByteBuf buf, TerragolemMalfunctionPayload value) {
            buf.writeLong(value.player.getMostSignificantBits()); buf.writeLong(value.player.getLeastSignificantBits());
            buf.writeFloat(value.heat); buf.writeInt(value.timeoutTicks);
            buf.writeInt(value.burstKind);
        }
    };
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TerragolemMalfunctionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.burstKind == 0) TerragolemMalfunctionRenderer.update(payload.player, payload.heat, payload.timeoutTicks);
            else TerragolemMalfunctionRenderer.burst(payload.player, payload.burstKind == 1);
        });
    }
}
