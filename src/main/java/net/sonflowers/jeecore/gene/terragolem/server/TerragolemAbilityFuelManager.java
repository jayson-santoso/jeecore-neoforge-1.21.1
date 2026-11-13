package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.world.entity.player.Player;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;

/**
 * Charges server ability costs through vanilla exhaustion, which spends saturation before hunger.
 */
public final class TerragolemAbilityFuelManager {
    public static final int HOLD_TICKS_PER_HUNGER = 20;
    public static final int OVERCHARGE_HUNGER_COST = 6;
    public static final int TIMEOUT_HUNGER_COST = 4;
    private static final float EXHAUSTION_PER_FOOD_POINT = 4.0F;

    private TerragolemAbilityFuelManager() {}

    /**
     * Match the three burst tiers: 1-2, 3-4, and 5-6 charged segments.
     */
    public static void consumeBurst(Player player, int segments) {
        if (segments <= 0) return;
        consume(player, segments <= 2 ? 2 : segments <= 4 ? 3 : 4);
    }

    private static boolean shouldCharge(Player player) {
        return !player.level().isClientSide && player.isAlive()
                && !player.getAbilities().instabuild && !player.isSpectator()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM;
    }

    public static void consume(Player player, int points) {
        if (!shouldCharge(player) || points <= 0) return;
        // Use the normal food tick, including its fractional exhaustion remainder and difficulty
        // rules. Never bypass stored saturation by directly subtracting from the hunger bar.
        player.causeFoodExhaustion(points * EXHAUSTION_PER_FOOD_POINT);
    }

    public static void tickHeldBeam(Player player) {
        if (!shouldCharge(player) || !TerragolemReadyUpManager.isHeld(player.getUUID())) return;
        var data = player.getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA);
        int ticks = data.heldFuelTicks() + 1;
        if (ticks >= HOLD_TICKS_PER_HUNGER) {
            consume(player, 1);
            ticks = 0;
        }
        // Keep partial usage across releases, menus, gene switches and relogs so tapping cannot
        // reset the meter. Death gets a fresh attachment alongside the normal hunger reset.
        data.setHeldFuelTicks(ticks);
    }
}
