package net.sonflowers.jeecore.gene.terragolem.network;

import java.util.UUID;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

/**
 * Clientbound payload. Clears client Terragolem visual state after a server reset.
 */
public record TerragolemResetPayload(UUID playerUUID) implements CustomPacketPayload {
    public static final Type<TerragolemResetPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_reset"));
    public static final StreamCodec<ByteBuf, TerragolemResetPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override public TerragolemResetPayload decode(ByteBuf buffer) {
            return new TerragolemResetPayload(new UUID(buffer.readLong(), buffer.readLong()));
        }
        @Override public void encode(ByteBuf buffer, TerragolemResetPayload payload) {
            buffer.writeLong(payload.playerUUID().getMostSignificantBits());
            buffer.writeLong(payload.playerUUID().getLeastSignificantBits());
        }
    };
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    public static void handle(TerragolemResetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> TerragolemReadyUpRenderer.reset(payload.playerUUID()));
    }
}
