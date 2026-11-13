package net.sonflowers.jeecore.gene.dryad.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModMobEffects;

/**
 * Applies spore protection and its periodic status behavior to eligible living entities.
 */
public final class SporeGuardEffect extends MobEffect {
    public static final int DURATION_TICKS = 240;
    public static final float SHIELD_AMOUNT = 4.0f;

    public SporeGuardEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x9DBB66);
        addAttributeModifier(Attributes.MAX_ABSORPTION,
                ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "spore_guard"),
                SHIELD_AMOUNT, AttributeModifier.Operation.ADD_VALUE);
    }

    public static boolean canProtect(LivingEntity target) {
        if (target.hasEffect(MobEffects.ABSORPTION)) return false;
        MobEffectInstance current = target.getEffect(ModMobEffects.SPORE_GUARD);
        if (current == null) return target.getAbsorptionAmount() <= 0.0f;
        // Never refill a spent shield, or take a pickup for a permanent/longer effect.
        return target.getAbsorptionAmount() > 0.0f && !current.isInfiniteDuration()
                && current.getDuration() < DURATION_TICKS;
    }

    public static boolean protect(LivingEntity target, LivingEntity source) {
        if (target.level().isClientSide || !canProtect(target)) return false;
        boolean fresh = !target.hasEffect(ModMobEffects.SPORE_GUARD);
        float previous = target.getAbsorptionAmount();
        if (!target.addEffect(new MobEffectInstance(ModMobEffects.SPORE_GUARD,
                DURATION_TICKS, 0, false, false, true), source)) return false;
        if (fresh) target.setAbsorptionAmount(Math.max(previous, SHIELD_AMOUNT));
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity target, int amplifier) {
        // Also handles absorption changed by another mod or command, outside damage events.
        return target.level().isClientSide || target.getAbsorptionAmount() > 0;
    }

    // Refreshes do not refill a partially spent shield. Vanilla removes the capacity modifier on
    // depletion, expiry, milk, and death.
}
