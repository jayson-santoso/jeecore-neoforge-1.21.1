package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

/**
 * Applies server-side Dryad passive behavior and reports ability-blocking conditions.
 */
public class DryadPassiveHandler {
    private static final int PHOTOSYNTHESIS_INTERVAL_TICKS = 300;
    private static final int FIRE_BLINDNESS_TICKS = 60;
    private static final int FIRE_OTHER_EFFECT_TICKS = 20;

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.DRYAD) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        tickFireWeakness(player);
        tickPhotosynthesis(player, level);
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            DryadSporeManager.tick(serverPlayer);
        }
    }

    public static boolean isAbilityBlocked(Player player) {
        return GeneHelper.getPlayerGene(player) == Gene.DRYAD && player.getRemainingFireTicks() > 0;
    }

    private static void tickFireWeakness(Player player) {
        if (player.getRemainingFireTicks() <= 0) return;

        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, FIRE_BLINDNESS_TICKS, 0, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, FIRE_OTHER_EFFECT_TICKS, 2, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FIRE_OTHER_EFFECT_TICKS, 1, false, false, false));
    }

    private static void tickPhotosynthesis(Player player, ServerLevel level) {
        if (player.tickCount % PHOTOSYNTHESIS_INTERVAL_TICKS != 0) return;

        FoodData foodData = player.getFoodData();
        if (foodData.getFoodLevel() >= 20) return;
        if (!isExposedToSun(player, level)) return;

        foodData.eat(1, 0.5f);
        level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    private static boolean isExposedToSun(Player player, ServerLevel level) {
        BlockPos pos = player.blockPosition();
        return level.isDay()
                && level.canSeeSky(pos.above())
                && !level.isRainingAt(pos);
    }
}
