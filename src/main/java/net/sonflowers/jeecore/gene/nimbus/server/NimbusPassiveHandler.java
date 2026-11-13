package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

/**
 * Applies Nimbus attributes and per-tick passive behavior, delegating glide and airborne control
 * to their managers.
 */
public class NimbusPassiveHandler {
    // Unique ResourceLocations for attribute modifiers.
    private static final ResourceLocation HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "nimbus_health_reduction");
    private static final ResourceLocation AIR_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "nimbus_air_speed");

    private static final double HEALTH_REDUCTION = -2.0; // -1 heart (2 health points).

    /**
     * Applies Nimbus health modifiers and removes conflicting fixed air-speed modifiers.
     */
    public static void applyPassiveAttributes(Player player) {
        // Reduce max health by 1 heart.
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null && healthAttr.getModifier(HEALTH_MODIFIER_ID) == null) {
            healthAttr.addPermanentModifier(new AttributeModifier(
                HEALTH_MODIFIER_ID,
                HEALTH_REDUCTION,
                AttributeModifier.Operation.ADD_VALUE
            ));
        }

        // Remove the fixed modifier so it cannot stack with Windrunner air control.
        AttributeInstance movementAttr = player.getAttribute(Attributes.MOVEMENT_EFFICIENCY);
        if (movementAttr != null) movementAttr.removeModifier(AIR_SPEED_MODIFIER_ID);
    }

    /**
     * Removes Nimbus passive attribute modifiers when the gene is deselected.
     */
    public static void removePassiveAttributes(Player player) {
        NimbusWindrunnerManager.clear(player);
        AttributeInstance healthAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            healthAttr.removeModifier(HEALTH_MODIFIER_ID);
        }

        AttributeInstance movementAttr = player.getAttribute(Attributes.MOVEMENT_EFFICIENCY);
        if (movementAttr != null) {
            movementAttr.removeModifier(AIR_SPEED_MODIFIER_ID);
        }
    }

    /**
     * Updates server Nimbus passives, including glide, Windrunner, and sneaking slow fall.
     */
    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.NIMBUS) return;

        // Always reset fall distance for Nimbus (no fall damage ever).
        player.fallDistance = 0;

        // Apply slow falling effect when sneaking in air.
        handleSlowfall(player);

        // Handle glide mechanics.
        NimbusGlideManager.tick(player);
        NimbusWindrunnerManager.tick(player);
    }

    private static void handleSlowfall(Player player) {
        // Check conditions: sneaking, not elytra flying, not in dash effect, in air.
        boolean shouldSlowfall = player.isShiftKeyDown() 
            && !player.isFallFlying() 
            && !DashEffectManager.isDashing(player.getUUID())
            && !player.onGround();

        if (shouldSlowfall) {
            // Apply slow falling effect (infinite duration, no particles, ambient).
            player.addEffect(new MobEffectInstance(
                MobEffects.SLOW_FALLING, 
                2, // 2 ticks duration (constantly refreshed).
                0, // Amplifier 0.
                true, // Ambient.
                false, // Show particles.
                false // Show icon.
            ));
        }
    }
}
