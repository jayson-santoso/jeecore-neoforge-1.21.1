package net.sonflowers.jeecore.gene.terragolem.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.terragolem.menu.TerragolemRepairMenu;

/**
 * Serverbound payload. Requests repair UI; the server checks the sender's gene, life state, and
 * current menu.
 */
public record OpenTerragolemRepairMenuPayload() implements CustomPacketPayload {
    public static final Type<OpenTerragolemRepairMenuPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "open_terragolem_repair"));
    public static final StreamCodec<ByteBuf, OpenTerragolemRepairMenuPayload> STREAM_CODEC =
            StreamCodec.unit(new OpenTerragolemRepairMenuPayload());
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(OpenTerragolemRepairMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.isAlive()
                    && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM
                    && !(player.containerMenu instanceof TerragolemRepairMenu)) {
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                        TerragolemRepairMenu.server(id, inventory), Component.translatable("menu.jeecore.terragolem_repair")));
            }
        });
    }
}
