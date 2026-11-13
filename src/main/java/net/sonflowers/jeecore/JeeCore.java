package net.sonflowers.jeecore;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.network.NetworkHandler;
import net.sonflowers.jeecore.core.network.OpenGeneScreenPayload;
import net.sonflowers.jeecore.gene.dryad.server.DryadSporeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;

/**
 * Initializes JeeCore registries and networking, and registers server commands.
 */
@Mod(JeeCore.MOD_ID)
public class JeeCore {
    public static final String MOD_ID = "jeecore";

    public JeeCore(IEventBus modEventBus, ModContainer modContainer) {
        net.sonflowers.jeecore.core.registry.ModItems.register(modEventBus);
        net.sonflowers.jeecore.core.registry.ModBlocks.BLOCKS.register(modEventBus);
        net.sonflowers.jeecore.core.registry.ModParticles.PARTICLES.register(modEventBus);
        net.sonflowers.jeecore.core.registry.ModMenus.MENUS.register(modEventBus);
        net.sonflowers.jeecore.core.registry.ModEntities.ENTITIES.register(modEventBus);
        net.sonflowers.jeecore.core.registry.ModMobEffects.EFFECTS.register(modEventBus);
        NetworkHandler.register(modEventBus);
        net.sonflowers.jeecore.core.gene.GeneDataAttachment.ATTACHMENT_TYPES.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onCommandsRegister(RegisterCommandsEvent event) {
        registerCommands(event.getDispatcher());
    }

    private void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("jeecore")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("choose")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(player, new OpenGeneScreenPayload());
                            return 1;
                        }))
                .then(Commands.literal("rage")
                        .then(Commands.literal("fill")
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> fillInfernalRage(EntityArgument.getPlayer(context, "target"))))
                                .executes(context -> fillInfernalRage(context.getSource().getPlayerOrException()))))
                .then(Commands.literal("spore")
                        .then(Commands.literal("spawn")
                                .executes(context -> {
                                    CommandSourceStack source = context.getSource();
                                    if (!DryadSporeManager.releaseFromCommand(source.getPlayerOrException())) {
                                        source.sendFailure(Component.translatable("commands.jeecore.spore.spawn_failed"));
                                        return 0;
                                    }
                                    source.sendSuccess(() -> Component.translatable("commands.jeecore.spore.spawned"), false);
                                    return 1;
                                }))
                        .then(Commands.literal("clear")
                                .executes(context -> {
                                    int count = DryadSporeManager.clearAll(context.getSource().getServer());
                                    context.getSource().sendSuccess(
                                            () -> Component.translatable("commands.jeecore.spore.cleared", count), false);
                                    return count;
                                }))));
    }

    private int fillInfernalRage(ServerPlayer player) {
        var data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        if (data.getGene() != Gene.INFERNAL) return 0;
        data.setRageBar(data.getMaxRageBar());
        InfernalRageManager.syncRageBar(player, data);
        return 1;
    }
}
