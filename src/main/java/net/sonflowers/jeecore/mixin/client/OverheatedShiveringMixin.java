package net.sonflowers.jeecore.mixin.client;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reuses the client powdered-snow shiver animation for overheating without applying freezing
 * mechanics.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class OverheatedShiveringMixin {
    @Inject(method = "isShaking", at = @At("RETURN"), cancellable = true)
    private void jeecore$overheatedShivering(LivingEntity entity, CallbackInfoReturnable<Boolean> result) {
        if (entity instanceof Player && entity.isAlive() && entity.hasEffect(ModMobEffects.OVERHEATED)) {
            result.setReturnValue(true);
        }
    }
}
