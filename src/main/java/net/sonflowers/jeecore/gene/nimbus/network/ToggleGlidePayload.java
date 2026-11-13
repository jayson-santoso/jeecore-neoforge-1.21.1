package net.sonflowers.jeecore.gene.nimbus.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.infernal.server.InfernalChargeManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusGlideManager;

/**
 * Serverbound payload. Requests the sender's mode toggle; Infernal uses ignition charge and other
 * senders reach the glide manager.
 */
public record ToggleGlidePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ToggleGlidePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "toggle_glide"));

    public static final StreamCodec<ByteBuf, ToggleGlidePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ToggleGlidePayload decode(ByteBuf buf) {
            return new ToggleGlidePayload();
        }

        @Override
        public void encode(ByteBuf buf, ToggleGlidePayload payload) {
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ToggleGlidePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (GeneHelper.getPlayerGene(player) == Gene.INFERNAL) {
                    InfernalChargeManager.toggle(player);
                } else {
                    NimbusGlideManager.toggleGlide(player);
                }
            }
        });
    }
}
