package net.sonflowers.jeecore.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemRepairManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Intercepts both FoodData regeneration checks before healing and exhaustion so Terragolem skips
 * only natural regeneration.
 */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    // Gate both regeneration branches before healing and its exhaustion charge. Blocking only
    // heal() drains saturation for healing that never happened. Activity exhaustion and starvation
    // remain outside these branches and are untouched.
    @Redirect(method = "tick(Lnet/minecraft/world/entity/player/Player;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isHurt()Z"),
            require = 2, expect = 2, allow = 2)
    private boolean jeecore$canNaturallyRegenerate(Player player) {
        return TerragolemRepairManager.allowsNaturalRegeneration(player) && player.isHurt();
    }
}
