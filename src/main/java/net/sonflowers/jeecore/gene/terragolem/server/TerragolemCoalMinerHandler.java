package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

/**
 * Awards server mining bonuses after successful removal and preserves Fortune and Silk Touch drop
 * behavior.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class TerragolemCoalMinerHandler {
    private static final float COAL_CHANCE = 0.10f;

    private TerragolemCoalMinerHandler() {}

    /**
     * Awards the stone-mining bonus after actual server block removal, including removal without a
     * harvesting tool.
     */
    public static void onBlockRemoved(ServerPlayer player, BlockPos pos, BlockState state) {
        if (!state.is(BlockTags.BASE_STONE_OVERWORLD) || !isCoalMiner(player)) return;
        if (!player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) return;
        if (player.getRandom().nextFloat() >= COAL_CHANCE) return;

        Block.popResource(player.serverLevel(), pos, new ItemStack(Items.COAL));
        showBonus(player, 1);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !isCoalMiner(player)) return;
        if (!event.getState().is(BlockTags.COAL_ORES)) return;

        // Duplicate actual coal drops so Fortune applies and Silk Touch stays intact.
        var coalDrops = event.getDrops().stream()
                .filter(drop -> drop.getItem().is(Items.COAL) && !drop.getItem().isEmpty())
                .toList();
        if (coalDrops.isEmpty() || player.getRandom().nextFloat() >= COAL_CHANCE) return;

        int bonusCount = 0;
        for (ItemEntity drop : coalDrops) {
            ItemEntity bonus = new ItemEntity(event.getLevel(), drop.getX(), drop.getY(), drop.getZ(),
                    drop.getItem().copy());
            bonus.setDefaultPickUpDelay();
            event.getDrops().add(bonus);
            bonusCount += bonus.getItem().getCount();
        }
        showBonus(player, bonusCount);
    }

    private static boolean isCoalMiner(ServerPlayer player) {
        return !player.isCreative() && !player.isSpectator()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM;
    }

    private static void showBonus(ServerPlayer player, int amount) {
        player.displayClientMessage(Component.translatable("message.jeecore.coal_miner_bonus", amount), true);
        TerragolemSoundManager.play(player, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0f, 2.0f);
    }
}
