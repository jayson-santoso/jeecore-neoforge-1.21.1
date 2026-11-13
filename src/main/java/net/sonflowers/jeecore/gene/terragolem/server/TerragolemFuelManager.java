package net.sonflowers.jeecore.gene.terragolem.server;

import java.util.Map;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.core.registry.ModParticles;
import net.minecraft.world.food.Foods;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModItems;
import net.sonflowers.jeecore.gene.terragolem.TerragolemTags;

/**
 * Defines fuel nutrition and repair tiers, validates use on both sides, and applies consumption on
 * the server.
 */
public final class TerragolemFuelManager {
    public static final int EATING_COOLDOWN_TICKS = 32;
    public static final float POWERED_HEAT_REDUCTION = 30.0F;
    public static final float POWERED_EATING_HEAT_REDUCTION = TerragolemOverheatManager.MAX_OVERHEAT * 0.3F;
    public static final float FUEL_SATURATION_MULTIPLIER = 0.5F;
    private record Fuel(FoodProperties food, boolean cooling) {
        private Fuel {
            // FoodProperties stores saturation points, not the legacy food modifier.
            food = new FoodProperties(food.nutrition(), food.saturation() * FUEL_SATURATION_MULTIPLIER,
                    food.canAlwaysEat(), food.eatSeconds(), food.usingConvertsTo(), food.effects());
        }
    }

    // Lazy initialization: deferred items have been registered before the first interaction.
    private static final class Values {
        private static final Map<Item, Fuel> TABLE = Map.of(
                Items.COAL, new Fuel(Foods.CARROT, false),
                Items.CHARCOAL, new Fuel(Foods.CARROT, false),
                ModItems.REFINED_COAL.get(), new Fuel(Foods.GOLDEN_CARROT, false),
                ModItems.POWERED_COAL.get(), new Fuel(Foods.COOKED_BEEF, true));
    }

    public static boolean isFuel(ItemStack stack) {
        return stack.is(TerragolemTags.Items.TERRAGOLEM_FUEL);
    }

    /**
     * Zero means unsupported; tier order is independent of healing amount.
     */
    public static int repairTier(ItemStack stack) {
        if (!isFuel(stack) || stack.isEmpty()) return 0;
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return 2;
        if (stack.is(ModItems.REFINED_COAL.get())) return 3;
        if (stack.is(ModItems.POWERED_COAL.get())) return 4;
        return 0;
    }

    public static float repairHearts(ItemStack stack) {
        return switch (repairTier(stack)) {
            case 2 -> 2.0F;
            case 3, 4 -> 3.0F;
            default -> 0.0F;
        };
    }

    public static InteractionResult consume(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM || !isFuel(stack)) {
            return InteractionResult.PASS;
        }
        Fuel fuel = Values.TABLE.get(stack.getItem());
        if (fuel == null) return InteractionResult.FAIL;
        // Check on both sides before acknowledging use. Full hunger never permits fuel use.
        if (!player.getFoodData().needsFood() || player.getCooldowns().isOnCooldown(stack.getItem())) {
            return InteractionResult.FAIL;
        }
        // Do not predict a swing: only an accepted server consumption animates the hand.
        if (player.level().isClientSide) return InteractionResult.CONSUME;
        player.getFoodData().eat(fuel.food);
        // Vanilla synchronizes these cooldowns to the client. Switching fuel or hands cannot bypass
        // them.
        for (Item item : Values.TABLE.keySet()) {
            player.getCooldowns().addCooldown(item, EATING_COOLDOWN_TICKS);
        }
        if (fuel.cooling) {
            TerragolemOverheatManager.reduceHeat(player, POWERED_EATING_HEAT_REDUCTION);
        }
        spawnRefuelingParticles(player, stack);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.swing(hand, true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.6F, 0.8F);
        return InteractionResult.CONSUME;
    }

    private static void spawnRefuelingParticles(Player player, ItemStack stack) {
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload(
                            sp.getUUID(), -1.0f, 0, 1));
        }
    }
}
