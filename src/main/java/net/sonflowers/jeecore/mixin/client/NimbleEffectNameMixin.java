package net.sonflowers.jeecore.mixin.client;

import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the inventory effect name on the client so Nimble always includes its level numeral.
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class NimbleEffectNameMixin {
    @Inject(method = "getEffectName", at = @At("HEAD"), cancellable = true)
    private void jeecore$showNimbleNumeral(MobEffectInstance effect, CallbackInfoReturnable<Component> result) {
        if (effect.is(ModMobEffects.NIMBLE)) result.setReturnValue(Component.translatable("effect.jeecore.nimble")
                .append(" ").append(Component.translatable("enchantment.level." + (effect.getAmplifier() + 1))));
    }
}
