package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Applies server gust movement and knockback within the aimed gameplay volume.
 */
public final class WindManager {
    public static final double RANGE = 8;
    public static final double HALF_ANGLE = Math.PI / 3;
    private WindManager() {}

    public static int applyGust(Player source, float charge) {
        if (source.level().isClientSide) return 0;
        Vec3 origin = source.getBoundingBox().getCenter();
        Vec3 facing = source.getLookAngle().normalize();
        double multiplier = 1 + 0.75 * Math.max(0, Math.min(1, charge));
        int affected = 0;
        for (LivingEntity target : source.level().getEntitiesOfClass(LivingEntity.class,
                source.getBoundingBox().inflate(RANGE), target -> target != source && target.isAlive() && !target.isSpectator())) {
            Vec3 offset = target.position().subtract(source.position());
            double distance = offset.length();
            if (distance <= 0 || distance > RANGE) continue;
            Vec3 aimOffset = target.getBoundingBox().getCenter().subtract(origin);
            if (aimOffset.lengthSqr() > 0.0001 && facing.dot(aimOffset.normalize()) < Math.cos(HALF_ANGLE) - 0.000001) continue;
            if (source.level().clip(new ClipContext(origin, target.getBoundingBox().getCenter(),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source)).getType() != HitResult.Type.MISS) continue;
            double falloff = Math.sqrt(Math.max(0, 1 - distance / RANGE));
            Vec3 direction = offset.normalize();
            target.setDeltaMovement(target.getDeltaMovement().add(direction.x * 1.75 * multiplier * falloff,
                    0.875 * multiplier * falloff, direction.z * 1.75 * multiplier * falloff));
            target.removeTag("jeecore_pushed");
            target.getTags().removeIf(tag -> tag.startsWith("trail_timer_") || tag.startsWith("trail_lifetime_"));
            target.addTag("jeecore_pushed");
            target.addTag("trail_timer_10");
            target.addTag("trail_lifetime_0");
            target.hurtMarked = true;
            target.clearFire();
            affected++;
        }
        source.clearFire();
        return affected;
    }
}

