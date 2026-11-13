package net.sonflowers.jeecore.gene.infernal.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.infernal.network.InfernalPassiveVisualPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Infernal attributes, environmental damage, and server ability hunger costs.
 */
public class InfernalPassiveHandler {
    private static final ResourceLocation ATTACK_SPEED_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_attack_speed");
    private static final Map<UUID, Integer> WATER_EXPOSURE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> VISUAL_SYNC_STATE = new ConcurrentHashMap<>();

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) {
            removePassiveAttributes(player);
            clearState(player);
            return;
        }

        applyPassiveAttributes(player);
        tickWaterWeakness(player);
        handleFireDamageImmunity(player);
        tickHeatRecovery(player);
        syncVisualState(player, true);
    }

    public static void applyPassiveAttributes(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null && !attackSpeed.hasModifier(ATTACK_SPEED_MODIFIER_ID)) {
            attackSpeed.addTransientModifier(new AttributeModifier(
                    ATTACK_SPEED_MODIFIER_ID,
                    0.45,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    public static void removePassiveAttributes(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed != null) {
            attackSpeed.removeModifier(ATTACK_SPEED_MODIFIER_ID);
        }
    }

    public static void drainAbilityHunger(Player player, float amount) {
        player.causeFoodExhaustion(amount);
    }

    private static void handleFireDamageImmunity(Player player) {
        if (player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(0);
        }
    }

    private static void tickWaterWeakness(Player player) {
        UUID uuid = player.getUUID();
        boolean exposed = isWaterExposed(player);
        // Separate tracking for water vs rain.
        boolean isWaterSubmerged = player.isInWater();
        int exposure = WATER_EXPOSURE_TICKS.getOrDefault(uuid, 0);
        if (!exposed || InfernalSmokeManager.isSmokeFormActive(player)) {
            WATER_EXPOSURE_TICKS.put(uuid, Math.max(0, exposure - 4));
            return;
        }

        exposure++;
        WATER_EXPOSURE_TICKS.put(uuid, exposure);

        // Submersion damage starts after 25 ticks and escalates with exposure.
        int interval;
        if (isWaterSubmerged) {
            // Water damage: starts harsh, escalates quickly.
            interval = exposure < 150 ? 25 : Math.max(8, 25 - (exposure - 150) / 10);
        } else {
            // Rain uses a separate exposure timer.
            interval = exposure < 400 ? 100 : Math.max(24, 100 - (exposure - 400) / 8);
        }

        if (exposure % interval == 0) {
            float damage;
            if (isWaterSubmerged) {
                damage = exposure < 150 ? 1.0f : Math.min(4.0f, 1.0f + (exposure - 150) / 150.0f * 3.0f);
            } else {
                damage = exposure < 400 ? 0.5f : Math.min(3.0f, 0.5f + (exposure - 400) / 400.0f);
            }
            player.hurt(player.damageSources().drown(), damage);
        }
    }

    private static boolean isWaterExposed(Player player) {
        if (player.isInWater()) return true;
        ItemStack helmet = player.getInventory().armor.get(3);
        if (!helmet.isEmpty()) return false;
        Level level = player.level();
        BlockPos pos = player.blockPosition();
        return level.isRainingAt(pos) || level.isRainingAt(pos.above());
    }

    private static void tickHeatRecovery(Player player) {
        if (player.tickCount % 40 != 0) return;
        if (!isNearHeatSource(player)) return;

        if (player.getHealth() < player.getMaxHealth()) {
            player.heal(player.isInLava() ? 1.0f : 0.5f);
        }
        if (player.getFoodData().getFoodLevel() < 20) {
            player.getFoodData().eat(player.isInLava() ? 2 : 1, 0.15f);
        } else if (player.getFoodData().getSaturationLevel() < 6.0f) {
            player.getFoodData().setSaturation(Math.min(6.0f, player.getFoodData().getSaturationLevel() + 0.25f));
        }
    }

    private static boolean isNearHeatSource(Player player) {
        if (player.isInLava()) return true;
        Level level = player.level();
        BlockPos origin = player.blockPosition();
        int radius = 4;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-radius, -2, -radius), origin.offset(radius, 2, radius))) {
            if (isHeatBlock(level.getBlockState(pos).getBlock())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHeatBlock(Block block) {
        return block == Blocks.CAMPFIRE
                || block == Blocks.SOUL_CAMPFIRE
                || block == Blocks.FIRE
                || block == Blocks.SOUL_FIRE
                || block == Blocks.LAVA
                || block == Blocks.LAVA_CAULDRON
                || block == Blocks.MAGMA_BLOCK
                || block == Blocks.FURNACE
                || block == Blocks.BLAST_FURNACE;
    }

    private static void syncVisualState(Player player, boolean active) {
        if (!(player instanceof ServerPlayer sp)) return;
        Boolean previous = VISUAL_SYNC_STATE.put(player.getUUID(), active);
        if (previous != null && previous == active && player.tickCount % 40 != 0) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new InfernalPassiveVisualPayload(player.getUUID(), active));
    }

    private static void clearState(Player player) {
        WATER_EXPOSURE_TICKS.remove(player.getUUID());
        if (VISUAL_SYNC_STATE.remove(player.getUUID()) != null && player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new InfernalPassiveVisualPayload(player.getUUID(), false));
        }
    }
}
