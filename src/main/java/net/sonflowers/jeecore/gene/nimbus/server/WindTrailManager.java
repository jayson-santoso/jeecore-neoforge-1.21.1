package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.sonflowers.jeecore.JeeCore;

/**
 * Emits server particles for gust-pushed living entities and clears their trail tags on expiry or
 * landing.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class WindTrailManager {

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;

        if (!entity.level().isClientSide() && entity.getTags().contains("jeecore_pushed")) {
            int currentTimer = 0;
            int lifetime = 0;
            String foundTimerTag = null;
            String foundLifetimeTag = null;

            for (String tag : entity.getTags()) {
                if (tag.startsWith("trail_timer_")) {
                    foundTimerTag = tag;
                    currentTimer = Integer.parseInt(tag.substring(12));
                } else if (tag.startsWith("trail_lifetime_")) {
                    foundLifetimeTag = tag;
                    lifetime = Integer.parseInt(tag.substring(15));
                }
            }

            if (entity.level() instanceof ServerLevel serverLevel) {
                Vec3 motion = entity.getDeltaMovement();
                for (int i = 0; i < 2; i++) {
                    float lerp = i / 2.0f;
                    double x = Mth.lerp(lerp, entity.xo, entity.getX());
                    double y = Mth.lerp(lerp, entity.yo, entity.getY());
                    double z = Mth.lerp(lerp, entity.zo, entity.getZ());
                    serverLevel.sendParticles(ParticleTypes.POOF, x, y + 0.5, z, 1,
                            motion.x * -0.5, motion.y * -0.5, motion.z * -0.5, 0.05);
                }
            }

            if (foundLifetimeTag != null) entity.removeTag(foundLifetimeTag);
            int newLifetime = lifetime + 1;
            entity.addTag("trail_lifetime_" + newLifetime);

            if (newLifetime > 60) {
                clearWindTags(entity, foundTimerTag, "trail_lifetime_" + newLifetime);
                return;
            }

            if (currentTimer > 0) {
                entity.removeTag(foundTimerTag);
                entity.addTag("trail_timer_" + (currentTimer - 1));
            } else {
                double speed = entity.getDeltaMovement().length();
                if (entity.onGround() || speed < 0.08) {
                    clearWindTags(entity, foundTimerTag, "trail_lifetime_" + newLifetime);
                }
            }
        }
    }

    private static void clearWindTags(LivingEntity entity, String timerTag, String lifeTag) {
        entity.removeTag("jeecore_pushed");
        if (timerTag != null) entity.removeTag(timerTag);
        if (lifeTag != null) entity.removeTag(lifeTag);
    }
}
