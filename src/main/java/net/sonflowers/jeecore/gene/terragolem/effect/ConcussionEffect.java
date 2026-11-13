package net.sonflowers.jeecore.gene.terragolem.effect;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModMobEffects;

/**
 * Defines the Terragolem concussion status applied by impact gameplay.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class ConcussionEffect extends MobEffect {
    public ConcussionEffect() {
        super(MobEffectCategory.HARMFUL, 0xA58F70);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, id("concussion_movement"), -1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(Attributes.JUMP_STRENGTH, id("concussion_jump"), -1,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    public static void restrain(LivingEntity entity) {
        if (!entity.hasEffect(ModMobEffects.CONCUSSION)) return;
        entity.setJumping(false);
        entity.xxa = entity.zza = 0;
        Vec3 velocity = entity.getDeltaMovement();
        entity.setDeltaMovement(0, velocity.y, 0);
        if (entity instanceof Mob mob) mob.getNavigation().stop();
        // Lock walking/jumping, while allowing the ripple's initial lift and normal gravity.
        if (!entity.level().isClientSide && velocity.horizontalDistanceSqr() > 0.000001) entity.hurtMarked = true;
    }

    @Override public void onEffectStarted(LivingEntity entity, int amplifier) {
        // Vanilla normally sends full effect state only to the affected player/passengers.
        // Observers need this effect too, so the orbit is visible above mobs and other players.
        if (entity.level() instanceof ServerLevel level) {
            var effect = entity.getEffect(ModMobEffects.CONCUSSION);
            if (effect != null) level.getChunkSource().broadcastAndSend(entity,
                    new ClientboundUpdateMobEffectPacket(entity.getId(), effect, false));
        }
    }

    @SubscribeEvent public static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof LivingEntity target) {
            var effect = target.getEffect(ModMobEffects.CONCUSSION);
            if (effect != null) observer.connection.send(new ClientboundUpdateMobEffectPacket(target.getId(), effect, false));
        }
    }

    private static void syncRemoval(LivingEntity entity) {
        if (entity.level() instanceof ServerLevel level) level.getChunkSource().broadcastAndSend(entity,
                new ClientboundRemoveMobEffectPacket(entity.getId(), ModMobEffects.CONCUSSION));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void expired(MobEffectEvent.Expired event) {
        var effect = event.getEffectInstance();
        if (effect != null && effect.is(ModMobEffects.CONCUSSION)) syncRemoval(event.getEntity());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public static void removed(MobEffectEvent.Remove event) {
        if (event.getEffect().is(ModMobEffects.CONCUSSION.getKey())) syncRemoval(event.getEntity());
    }

    @SubscribeEvent public static void beforeEntityTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof LivingEntity entity) restrain(entity);
    }

    @SubscribeEvent public static void afterEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity) restrain(entity);
    }

    // Player ticks have their own hooks, including local prediction on the client.
    @SubscribeEvent public static void beforePlayerTick(PlayerTickEvent.Pre event) { restrain(event.getEntity()); }
    @SubscribeEvent public static void afterPlayerTick(PlayerTickEvent.Post event) { restrain(event.getEntity()); }

    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, path); }
}
