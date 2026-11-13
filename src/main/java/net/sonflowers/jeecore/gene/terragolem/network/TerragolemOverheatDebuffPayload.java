package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

/**
 * Clientbound payload. Triggers local overheat-debuff camera and flash feedback.
 */
public record TerragolemOverheatDebuffPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerragolemOverheatDebuffPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_overheat_debuff"));

    public static final StreamCodec<ByteBuf, TerragolemOverheatDebuffPayload> STREAM_CODEC =
            StreamCodec.unit(new TerragolemOverheatDebuffPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TerragolemOverheatDebuffPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.flow().getReceptionSide().isClient()) {
                TerragolemReadyUpRenderer.triggerOverheatDebuff();
            }
        });
    }
}
