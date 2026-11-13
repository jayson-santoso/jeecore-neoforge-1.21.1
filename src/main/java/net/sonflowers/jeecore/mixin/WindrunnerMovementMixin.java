package net.sonflowers.jeecore.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerMovement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Redirects the vanilla moveRelative call during friction-based movement to apply conditional
 * Windrunner air control.
 */
@Mixin(LivingEntity.class)
public abstract class WindrunnerMovementMixin {
    @Redirect(method = "handleRelativeFrictionAndCalculateMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;moveRelative(FLnet/minecraft/world/phys/Vec3;)V"))
    private void jeecore$windrunnerAirControl(LivingEntity entity, float acceleration, Vec3 input) {
        WindrunnerMovement.moveRelative(entity, acceleration, input);
    }
}
