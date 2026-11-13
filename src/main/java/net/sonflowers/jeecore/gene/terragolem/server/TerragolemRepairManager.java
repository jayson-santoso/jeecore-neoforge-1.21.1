package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.terragolem.menu.TerragolemRepairMenu;

/**
 * Validates server repair starts and completion, including cooldowns, reserved fuel, and storage
 * cleanup.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class TerragolemRepairManager {
    public static final int POWERED_REGENERATION_TICKS = 100;
    public static final int REPAIR_CLICK_COOLDOWN_TICKS = 40;
    public static final int MISSED_REPAIR_COOLDOWN_TICKS = 100;

    public static int cooldownRemaining(Player player) {
        long remaining = player.getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA).nextRepairTick()
                - player.level().getGameTime();
        return (int) Math.clamp(remaining, 0L, MISSED_REPAIR_COOLDOWN_TICKS);
    }

    public static boolean repair(Player player, TerragolemRepairMenu menu) {
        if (player.level().isClientSide || player.containerMenu != menu || !menu.stillValid(player)
                || cooldownRemaining(player) > 0 || player.getHealth() >= player.getMaxHealth()) return false;
        if (player.getFoodData().getFoodLevel() <= 0) return false;
        int fuelSlot = menu.selectedFuelSlot();
        if (fuelSlot < 0) return false;
        if (menu.round().active() || menu.selectedRepairHearts() <= 0) return false;
        return menu.round().start(fuelSlot);
    }

    /**
     * Invoked only by the menu-owned server round after its three timed targets finish.
     */
    public static float completeRound(Player player, TerragolemRepairMenu menu, ItemStack fuel, float multiplier) {
        if (player.level().isClientSide || player.containerMenu != menu || !menu.stillValid(player)
                || !menu.round().active() || player.getFoodData().getFoodLevel() <= 0) return 0;
        float hearts = TerragolemFuelManager.repairHearts(fuel);
        if (hearts <= 0 || multiplier < 0.5F || multiplier > 1.5F) return 0;
        float before = player.getHealth();
        if (before >= player.getMaxHealth()) return 0;
        player.heal(Math.min(hearts * 2.0F * multiplier, player.getMaxHealth() - before));
        float healed = player.getHealth() - before;
        if (healed <= 0) return 0;
        if (TerragolemFuelManager.repairTier(fuel) == 4) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, POWERED_REGENERATION_TICKS, 0));
            TerragolemOverheatManager.reduceHeat(player, TerragolemFuelManager.POWERED_HEAT_REDUCTION);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.IRON_GOLEM_REPAIR,
                SoundSource.PLAYERS, 0.35F, 1.0F);
        player.level().playSound(null, player.blockPosition(),
                player.getRandom().nextBoolean() ? SoundEvents.ANVIL_USE : SoundEvents.SMITHING_TABLE_USE,
                SoundSource.PLAYERS, 0.7F, 1.65F + player.getRandom().nextFloat() * 0.25F);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload(
                            sp.getUUID(), -1.0f, 0, 2));
            sp.serverLevel().sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    sp.getX(), sp.getY() + sp.getBbHeight() * 0.65, sp.getZ(),
                    6, 0.35, 0.45, 0.35, 0.02);
        }
        return healed;
    }

    @SubscribeEvent
    public static void dropStoredFuel(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        // Stored fuel drops even with keepInventory, and even after switching away from Terragolem.
        if (player.containerMenu instanceof TerragolemRepairMenu menu) {
            addFuelDrop(event, player, menu.round().takeReservedFuel());
            addFuelDrop(event, player, menu.takeFixFuel());
        }
        if (!player.hasData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA)) return;
        var storage = player.getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA).fuelInventory();
        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack stack = storage.getStackInSlot(slot);
            storage.setStackInSlot(slot, ItemStack.EMPTY);
            addFuelDrop(event, player, stack);
        }
    }

    private static void addFuelDrop(LivingDropsEvent event, Player player, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity drop = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), stack);
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    public static boolean allowsNaturalRegeneration(Player player) {
        return GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM;
    }

    @SubscribeEvent public static void closeInvalidMenu(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.containerMenu instanceof TerragolemRepairMenu menu) {
            if (!menu.stillValid(player)) player.closeContainer();
            else menu.round().tick();
        }
    }
}
