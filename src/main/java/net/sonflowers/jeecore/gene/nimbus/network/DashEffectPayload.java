package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.DashOverlayRenderer;

/**
 * Clientbound payload. Updates the local dash overlay and transient dash flag.
 */
public record DashEffectPayload(boolean active) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DashEffectPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "dash_effect"));

    public static final StreamCodec<ByteBuf, DashEffectPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    DashEffectPayload::active,
                    DashEffectPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DashEffectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                DashOverlayRenderer.setActive(payload.active);
                Minecraft.getInstance().player.getData(net.sonflowers.jeecore.core.gene.GeneDataAttachment.WINDRUNNER).clientDashing = payload.active;
            }
        });
    }
}
