package net.sonflowers.jeecore.mixin;

import net.minecraft.world.entity.Mob;
import net.sonflowers.jeecore.gene.dryad.server.DryadRootSnareManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Guards Mob.setNoAi at entry so an active server root snare retains its AI lock until cleanup.
 */
@Mixin(Mob.class)
public abstract class MobNoAiMixin {
    @Inject(method = "setNoAi(Z)V", at = @At("HEAD"), cancellable = true)
    private void jeecore$retainRootSnareAiLock(boolean noAi, CallbackInfo ci) {
        if (!noAi && DryadRootSnareManager.preventAiReset((Mob) (Object) this)) ci.cancel();
    }
}
