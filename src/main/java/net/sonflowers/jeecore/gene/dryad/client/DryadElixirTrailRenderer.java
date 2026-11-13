package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks client elixir entities and renders their transient trail, sparkle, and spiral particles.
 */
public class DryadElixirTrailRenderer {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<Integer, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Map<Integer, Integer> MISSING_TICKS = new HashMap<>();
    private static final Set<Integer> ACTIVE_ELIXIRS = new HashSet<>();
    private static final java.util.List<TrailParticle> PARTICLES = new java.util.ArrayList<>();

    public static void track(int entityId) {
        ACTIVE_ELIXIRS.add(entityId);
        MISSING_TICKS.put(entityId, 0);
    }

    public static boolean hasParticles() {
        return !PARTICLES.isEmpty() || !ACTIVE_ELIXIRS.isEmpty();
    }

    public static boolean hasVisibleElixirs(Level level, AABB bounds) {
        return !ACTIVE_ELIXIRS.isEmpty();
    }

    public static void render(Level level, Vec3 camera, BufferBuilder buffer, Matrix4f matrix, double gameTime) {
        Iterator<Integer> idIterator = ACTIVE_ELIXIRS.iterator();
        while (idIterator.hasNext()) {
            int id = idIterator.next();
            Entity entity = level.getEntity(id);
            if (!(entity instanceof Display.BlockDisplay elixir)) {
                int missing = MISSING_TICKS.getOrDefault(id, 0) + 1;
                if (missing > 12) {
                    idIterator.remove();
                    LAST_POSITIONS.remove(id);
                    MISSING_TICKS.remove(id);
                } else {
                    MISSING_TICKS.put(id, missing);
                }
                continue;
            }

            MISSING_TICKS.put(id, 0);
            Vec3 pos = elixir.position().add(0.18, 0.18, 0.18);
            Vec3 previous = LAST_POSITIONS.get(id);
            if (previous != null && previous.distanceToSqr(pos) > 0.001) {
                // Keep trail emission sparse so it does not obscure the elixir.
                spawnTrail(previous.lerp(pos, 0.35), previous.subtract(pos), gameTime);
                spawnTrail(previous.lerp(pos, 0.7), previous.subtract(pos), gameTime);

                // Add spiral only occasionally for accent.
                if (RANDOM.nextInt(3) == 0) {
                    spawnSpiralParticles(previous.lerp(pos, 0.5), previous.subtract(pos), gameTime);
                }
            }
            LAST_POSITIONS.put(id, pos);
        }

        Iterator<TrailParticle> iterator = PARTICLES.iterator();
        while (iterator.hasNext()) {
            TrailParticle particle = iterator.next();
            if (!particle.tick()) {
                iterator.remove();
                continue;
            }
            if (camera.distanceToSqr(particle.pos) < 0.25) continue;
            drawParticle(buffer, matrix, particle, camera, gameTime);
        }
    }

    private static void spawnTrail(Vec3 pos, Vec3 backward, double gameTime) {
        Vec3 direction = backward.lengthSqr() > 0.001 ? backward.normalize() : Vec3.ZERO;

        // Keep the main trail visible without obscuring nearby gameplay.
        for (int i = 0; i < 3; i++) { 
            Vec3 jitter = new Vec3(
                    (RANDOM.nextDouble() - 0.5) * 0.2,
                    (RANDOM.nextDouble() - 0.5) * 0.2,
                    (RANDOM.nextDouble() - 0.5) * 0.2);
            Vec3 motion = direction.scale(0.015 + RANDOM.nextDouble() * 0.025).add(jitter.scale(0.08));
            PARTICLES.add(new TrailParticle(pos.add(jitter), motion, ParticleType.MAIN, gameTime));
        }

        // Use sparse sparkles to keep highlights distinct.
        for (int i = 0; i < 1; i++) { 
            Vec3 jitter = new Vec3(
                    (RANDOM.nextDouble() - 0.5) * 0.15,
                    (RANDOM.nextDouble() - 0.5) * 0.15,
                    (RANDOM.nextDouble() - 0.5) * 0.15);
            Vec3 motion = direction.scale(0.005).add(jitter.scale(0.15));
            PARTICLES.add(new TrailParticle(pos.add(jitter), motion, ParticleType.SPARKLE, gameTime));
        }
    }

    private static void spawnSpiralParticles(Vec3 pos, Vec3 backward, double gameTime) {
        Vec3 direction = backward.lengthSqr() > 0.001 ? backward.normalize() : Vec3.ZERO;
        Vec3 perpendicular = direction.cross(new Vec3(0, 1, 0));
        if (perpendicular.lengthSqr() < 0.001) perpendicular = new Vec3(1, 0, 0);
        perpendicular = perpendicular.normalize();

        // Keep spiral accents sparse.
        for (int i = 0; i < 1; i++) { 
            float angle = (float) (gameTime * 0.5 + i * Math.PI);
            float radius = 0.3f;
            Vec3 offset = perpendicular.scale(Math.cos(angle) * radius)
                    .add(new Vec3(0, Math.sin(angle) * radius, 0));
            Vec3 spiralPos = pos.add(offset);
            Vec3 motion = offset.scale(0.02).add(direction.scale(0.01));
            PARTICLES.add(new TrailParticle(spiralPos, motion, ParticleType.SPIRAL, gameTime));
        }
    }

    private static void drawParticle(BufferBuilder buffer, Matrix4f matrix, TrailParticle particle, Vec3 camera, double gameTime) {
        Vec3 dir = particle.pos.subtract(camera).normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 0.001) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = dir.cross(right).normalize();

        float life = 1.0f - ((float) particle.age / particle.maxAge);
        float pulse = 0.8f + (float) Math.sin(gameTime * 0.9 + particle.seed) * 0.2f;
        float size = particle.size * life * pulse;

        int alpha, r, g, b;

        switch (particle.type) {
            case SPARKLE:
                // Bright white-yellow sparkles.
                alpha = (int) (255 * life * life); // Faster fade for twinkle effect.
                r = 255;
                g = 255;
                b = 220 + (int) (35 * pulse);
                size *= 0.7f; // Smaller sparkles.
                break;

            case SPIRAL:
                // Vibrant green spiral particles.
                alpha = (int) (200 * life);
                r = 100 + (int) (55 * pulse);
                g = 255;
                b = 120 + (int) (35 * pulse);
                size *= 0.85f;
                break;

            case MAIN:
            default:
                // Main golden-yellow trail.
                alpha = (int) (240 * life);
                r = 255;
                g = 240 - (int) (40 * (1.0f - life)); // Transitions to more orange.
                b = 90 + (int) (40 * life); // Greenish tint.
                break;
        }

        Vec3 rr = right.scale(size);
        Vec3 uu = up.scale(size);

        // Draw quad with gradient from center to edges.
        int centerAlpha = alpha;
        int edgeAlpha = alpha / 3;

        // Center is bright, edges fade for glow effect.
        buffer.addVertex(matrix, (float) (particle.pos.x - rr.x - uu.x), (float) (particle.pos.y - rr.y - uu.y), (float) (particle.pos.z - rr.z - uu.z)).setColor(r, g, b, edgeAlpha);
        buffer.addVertex(matrix, (float) (particle.pos.x + rr.x - uu.x), (float) (particle.pos.y + rr.y - uu.y), (float) (particle.pos.z + rr.z - uu.z)).setColor(r, g, b, edgeAlpha);
        buffer.addVertex(matrix, (float) (particle.pos.x + rr.x + uu.x), (float) (particle.pos.y + rr.y + uu.y), (float) (particle.pos.z + rr.z + uu.z)).setColor(r, g, b, centerAlpha);
        buffer.addVertex(matrix, (float) (particle.pos.x - rr.x + uu.x), (float) (particle.pos.y - rr.y + uu.y), (float) (particle.pos.z - rr.z + uu.z)).setColor(r, g, b, centerAlpha);
    }

    private enum ParticleType {
        MAIN,     // Main golden trail particles.
        SPARKLE,  // Bright white sparkles.
        SPIRAL    // Green spiral particles.
    }

    private static class TrailParticle {
        Vec3 pos;
        Vec3 motion;
        int age;
        final int maxAge;
        final float size;
        final float seed;
        final ParticleType type;

        TrailParticle(Vec3 pos, Vec3 motion, ParticleType type, double gameTime) {
            this.pos = pos;
            this.motion = motion;
            this.type = type;

            switch (type) {
                case SPARKLE:
                    this.maxAge = 10 + RANDOM.nextInt(6); // Short-lived sparkles.
                    this.size = 0.12f + RANDOM.nextFloat() * 0.06f;
                    break;
                case SPIRAL:
                    this.maxAge = 18 + RANDOM.nextInt(8);
                    this.size = 0.09f + RANDOM.nextFloat() * 0.04f;
                    break;
                case MAIN:
                default:
                    this.maxAge = 22 + RANDOM.nextInt(12); // Longer lasting main trail.
                    this.size = 0.13f + RANDOM.nextFloat() * 0.07f;
                    break;
            }

            this.seed = RANDOM.nextFloat() * 100.0f + (float) gameTime;
        }

        boolean tick() {
            pos = pos.add(motion);

            // Different physics for different particle types.
            if (type == ParticleType.SPARKLE) {
                motion = motion.scale(0.88).add(0.0, -0.003, 0.0); // Sparkles fall slightly.
            } else if (type == ParticleType.SPIRAL) {
                motion = motion.scale(0.85).add(0.0, 0.004, 0.0); // Spiral rises slightly.
            } else {
                motion = motion.scale(0.80).add(0.0, 0.003, 0.0); // Main trail drifts up.
            }

            age++;
            return age < maxAge;
        }
    }
}
