package net.sonflowers.jeecore.gene.nimbus.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.sonflowers.jeecore.JeeCore;

/**
 * Provides the ground-speed bonus per level; airborne control belongs to WindrunnerMovement.
 */
public final class NimbleEffect extends MobEffect {
    public NimbleEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xB5EDFF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "nimble_movement"),
                .05, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }
}
