package net.sonflowers.jeecore.gene.terragolem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModMobEffects;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Renders client critical-hit particles orbiting the head, with two smaller trailing points per
 * star.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class ConcussionStars {
    private static final Map<Integer, Orbit> ORBITS = new HashMap<>();
    private static ClientLevel level;

    private static final class Orbit {
        final Particle[] particles = new Particle[9];
        long refreshed = Long.MIN_VALUE;
        void clear() {
            for (int i = 0; i < particles.length; i++) {
                if (particles[i] != null) particles[i].remove();
                particles[i] = null;
            }
        }
    }

    private ConcussionStars() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (level != mc.level || mc.player == null) clear();
        level = mc.level;
        if (level == null || mc.player == null || mc.isPaused()) return;
        long now = level.getGameTime();
        var visible = new HashSet<Integer>();
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(48),
                target -> target.isAlive() && !target.isSpectator() && target.hasEffect(ModMobEffects.CONCUSSION))) {
            visible.add(entity.getId());
            Orbit orbit = ORBITS.computeIfAbsent(entity.getId(), ignored -> new Orbit());
            boolean refresh = orbit.refreshed == Long.MIN_VALUE || now - orbit.refreshed >= 4;
            if (refresh) { orbit.clear(); orbit.refreshed = now; }
            float radius = Mth.clamp(entity.getBbWidth() * 0.6f, 0.35f, 0.8f);
            for (int index = 0; index < orbit.particles.length; index++) {
                int i = index / 3;
                int trail = index % 3;
                double phase = entity.getId() * 0.7 + i * Math.PI * 2 / 3;
                double angle = now * 0.36 + phase - trail * 0.22;
                double x = entity.getX() + Math.cos(angle) * radius;
                double bob = Math.sin(now * 0.20 + phase - trail * 0.15) * 0.10;
                double y = entity.getY() + entity.getBbHeight() + 0.30 + bob;
                double z = entity.getZ() + Math.sin(angle) * radius;
                if (refresh) {
                    Particle particle = mc.particleEngine.createParticle(ParticleTypes.CRIT, x, y, z, 0, 0, 0);
                    if (particle != null) {
                        particle.setLifetime(12);
                        particle.scale(trail == 0 ? 0.9f : trail == 1 ? 0.5f : 0.28f);
                        orbit.particles[index] = particle;
                    }
                }
                Particle particle = orbit.particles[index];
                if (particle != null) {
                    particle.setParticleSpeed(0, 0, 0);
                    particle.setPos(x, y, z);
                }
            }
        }
        ORBITS.entrySet().removeIf(entry -> {
            if (visible.contains(entry.getKey())) return false;
            entry.getValue().clear();
            return true;
        });
    }

    private static void clear() {
        ORBITS.values().forEach(Orbit::clear);
        ORBITS.clear();
        level = null;
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) clear();
    }
}
