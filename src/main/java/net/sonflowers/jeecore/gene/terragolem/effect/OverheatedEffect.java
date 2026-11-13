package net.sonflowers.jeecore.gene.terragolem.effect;

import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemOverheatManager;

/**
 * Defines the Terragolem overheat debuff and synchronizes its visible entity effect state.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class OverheatedEffect extends MobEffect {
    public static final ResourceLocation SLOWDOWN_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "overheated_movement");

    public OverheatedEffect() {
        super(MobEffectCategory.HARMFUL, 0xFF7135);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, SLOWDOWN_ID, -0.35,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) { return true; }

    @Override public boolean applyEffectTick(LivingEntity target, int amplifier) {
        return target.level().isClientSide || (target instanceof Player player && player.isAlive()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM
                && TerragolemOverheatManager.isPrimaryLocked(player.getUUID()));
    }

    @Override public void onEffectStarted(LivingEntity target, int amplifier) {
        if (target.level() instanceof ServerLevel level) {
            var effect = target.getEffect(ModMobEffects.OVERHEATED);
            if (effect != null) level.getChunkSource().broadcastAndSend(target,
                    new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
        }
    }

    @SubscribeEvent public static void track(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof LivingEntity target) {
            var effect = target.getEffect(ModMobEffects.OVERHEATED);
            if (effect != null) observer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
        }
    }

    private static void syncRemoval(LivingEntity target) {
        if (target.level() instanceof ServerLevel level) level.getChunkSource().broadcastAndSend(target,
                new ClientboundRemoveMobEffectPacket(target.getId(), ModMobEffects.OVERHEATED));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void expired(MobEffectEvent.Expired event) {
        var effect = event.getEffectInstance();
        if (effect != null && effect.is(ModMobEffects.OVERHEATED)) syncRemoval(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void removed(MobEffectEvent.Remove event) {
        if (event.getEffect().is(ModMobEffects.OVERHEATED.getKey())) syncRemoval(event.getEntity());
    }
}
