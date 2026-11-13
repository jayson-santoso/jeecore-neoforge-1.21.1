package net.sonflowers.jeecore.gene.nimbus;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.server.DashEffectManager;

/**
 * Applies Windrunner air control at the vanilla movement call site, retaining normal movement for
 * other entities.
 */
public final class WindrunnerMovement {
    private WindrunnerMovement() {}
    public static void moveRelative(LivingEntity entity, float acceleration, Vec3 input) {
        if (!(entity instanceof Player p) || GeneHelper.getPlayerGene(p) != Gene.NIMBUS) {
            entity.moveRelative(acceleration, input);
            return;
        }
        var s = p.getData(GeneDataAttachment.WINDRUNNER);
        float target = p.level().isClientSide ? s.clientStage : s.rules.stage();
        s.movementStage += Math.clamp(target - s.movementStage, -1f / 6, 1f / 6);
        if (s.movementStage <= .001 || p.onGround() || p.isFallFlying() || p.isInWater() || p.isInLava()
                || p.onClimbable() || p.isPassenger() || p.getAbilities().flying || p.isSpectator()
                || p.hurtTime > 0 || s.clientDashing || DashEffectManager.isDashing(p.getUUID())) {
            entity.moveRelative(acceleration, input);
            return;
        }
        double length = input.lengthSqr();
        if (length < 1.0e-7) return;
        Vec3 relative = length > 1 ? input.normalize() : input;
        double yaw = Math.toRadians(p.getYRot());
        double dx = relative.x * Math.cos(yaw) - relative.z * Math.sin(yaw);
        double dz = relative.z * Math.cos(yaw) + relative.x * Math.sin(yaw);
        Vec3 velocity = p.getDeltaMovement();
        var result = WindrunnerAirControl.move(velocity.x, velocity.z, dx, dz, acceleration,
                acceleration / .09, s.movementStage);
        p.setDeltaMovement(result.x(), velocity.y, result.z());
    }
}
