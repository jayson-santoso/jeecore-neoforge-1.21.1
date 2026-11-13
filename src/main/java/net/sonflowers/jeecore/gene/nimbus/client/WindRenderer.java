package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders transient client gust shells and ground particles from server release parameters.
 */
public class WindRenderer {
    private static final int LIFETIME = 16;
    private static final float HALF_ARC = (float) (Math.PI / 3);
    private static final int SLICES = 40;

    public static final class ActiveBurst {
        public final Vec3 pos, direction;
        public final float radius, charge;
        public final long startTime;
        ActiveBurst(Vec3 pos, Vec3 direction, float radius, float charge, long startTime) {
            this.pos = pos;
            this.direction = direction.normalize();
            this.radius = radius;
            this.charge = Mth.clamp(charge, 0, 1);
            this.startTime = startTime;
        }
    }
    public static final List<ActiveBurst> BURSTS = new ArrayList<>();
    public static float screenShakeIntensity;
    private static ClientLevel level;

    public static void prune() {
        ClientLevel current = Minecraft.getInstance().level;
        if (level != current) { BURSTS.clear(); level = current; }
        if (current != null) BURSTS.removeIf(burst -> current.getGameTime() - burst.startTime >= LIFETIME);
    }

    public static void trigger(Vec3 pos, Vec3 direction, float radius, float charge) {
        prune();
        if (level == null) return;
        BURSTS.add(new ActiveBurst(pos, direction, radius, charge, level.getGameTime()));
        spawnGroundClouds(pos, direction.normalize(), Mth.clamp(charge, 0, 1));
        var player = Minecraft.getInstance().player;
        if (player != null) {
            float proximity = Mth.clamp(1 - (float) player.getBoundingBox().getCenter().distanceTo(pos) / 4, 0, 1);
            screenShakeIntensity = Math.max(screenShakeIntensity,
                    (0.24f + Mth.clamp(charge, 0, 1) * 0.22f) * proximity);
        }
    }

    private static void spawnGroundClouds(Vec3 origin, Vec3 axis, float charge) {
        Vec3 right = axis.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(axis).normalize();
        int count = 8 + (int) (charge * 6);
        for (int i = 0; i < count; i++) {
            // Sample the forward fan, then find a real floor instead of emitting in midair.
            float angle = (level.random.nextFloat() - 0.5f) * HALF_ARC * 1.5f;
            Vec3 flow = axis.scale(Mth.cos(angle)).add(right.scale(Mth.sin(angle)))
                    .add(up.scale(0.12 + level.random.nextDouble() * 0.18)).normalize();
            Vec3 sample = origin.add(flow.scale(0.5 + level.random.nextDouble() * 2));
            var hit = level.clip(new ClipContext(sample.add(0, 0.5, 0), sample.add(0, -4, 0),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
            if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP) continue;
            Vec3 point = hit.getLocation().add(0, 0.06, 0);
            Vec3 velocity = flow.scale(0.65 + charge * 0.45 + level.random.nextDouble() * 0.2);
            level.addParticle(ParticleTypes.CLOUD, point.x, point.y, point.z, velocity.x, velocity.y, velocity.z);
        }
    }

    public static void render(ActiveBurst burst, BufferBuilder buffer, Matrix4f matrix, double now) {
        float age = (float) (now - burst.startTime);
        if (age < 0 || age >= LIFETIME) return;
        Vec3 axis = burst.direction;
        Vec3 right = axis.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(axis).normalize();
        float charge = burst.charge;
        float progress = Mth.clamp(age / 12, 0, 1);
        float radius = burst.radius * (1 - (1 - progress) * (1 - progress));
        float fade = (1 - progress) * Math.min(age * 2, 1);

        // A translucent spherical cap follows the complete gameplay cone, including above/below the
        // aim.
        for (int ring = 0; ring < 6; ring++) {
            float inner = HALF_ARC * ring / 6;
            float outer = HALF_ARC * (ring + 1) / 6;
            for (int slice = 0; slice < SLICES; slice++) {
                float a = Mth.TWO_PI * slice / SLICES;
                float b = Mth.TWO_PI * (slice + 1) / SLICES;
                float ripple = 0.85f + 0.15f * Mth.sin(a * 5 - age * 1.7f + ring);
                int alpha = (int) ((12 + 20 * charge) * fade * (1 - ring / 7f) * ripple);
                quad(buffer, matrix, burst, axis, right, up, radius, inner, outer, a, b, alpha);
            }
        }

        // Curving bands rotate at different speeds over the shell; longer charges add finer layers.
        int bands = 2 + (int) (charge * 3);
        for (int band = 0; band < bands; band++) {
            float polar = HALF_ARC * (band + 1) / (bands + 1);
            float thickness = (0.017f + 0.015f * charge) * (0.65f + 0.7f * (0.5f + 0.5f * Mth.sin(band * 2.4f + 0.8f)));
            for (int slice = 0; slice < SLICES; slice++) {
                float a = Mth.TWO_PI * slice / SLICES;
                float b = Mth.TWO_PI * (slice + 1) / SLICES;
                float twist = age * (0.25f + band * 0.035f);
                float arcFade = Mth.clamp((Mth.sin((a + b) * 0.5f * 2 - twist + band) + 0.3f) / 1.3f, 0, 1);
                float flutterA = Mth.sin(a * 3 + twist) * (0.035f + 0.045f * charge);
                float flutterB = Mth.sin(b * 3 + twist) * (0.035f + 0.045f * charge);
                float opaqueCore = Mth.clamp((arcFade - 0.45f) / 0.55f, 0, 1);
                int alpha = (int) (fade * Math.min(255, (55 + 120 * charge) * arcFade + 80 * opaqueCore));
                if (alpha <= 0 || radius <= 0) continue;
                vertex(buffer, matrix, burst.pos, axis, right, up, radius, polar + flutterA - thickness, a, alpha);
                vertex(buffer, matrix, burst.pos, axis, right, up, radius, polar + flutterB - thickness, b, alpha);
                vertex(buffer, matrix, burst.pos, axis, right, up, radius, polar + flutterB + thickness, b, alpha);
                vertex(buffer, matrix, burst.pos, axis, right, up, radius, polar + flutterA + thickness, a, alpha);
            }
        }

        // Narrow meridians make the depth and spherical curvature legible from the caster's view.
        int streaks = 5 + (int) charge;
        for (int streak = 0; streak < streaks; streak++) {
            float azimuth = Mth.TWO_PI * streak / streaks + age * 0.08f;
            for (int segment = 0; segment < 8; segment++) {
                float inner = HALF_ARC * segment / 8;
                float outer = HALF_ARC * (segment + 1) / 8;
                int alpha = (int) ((60 + 70 * charge) * fade * Mth.sin((segment + 0.5f) / 8 * Mth.PI));
                quad(buffer, matrix, burst, axis, right, up, radius, inner, outer,
                        azimuth - streakWidth(streak), azimuth + streakWidth(streak), alpha);
            }
        }

        // Delayed shock fronts cross the same range in five ticks, overtaking the slower main cap.
        int echoes = 1 + (int) charge;
        for (int echo = 0; echo < echoes; echo++) {
            float travel = (age - 2 - echo * 2) / 5;
            if (travel <= 0 || travel >= 1) continue;
            float echoRadius = burst.radius * travel;
            float echoFade = Mth.sin(travel * Mth.PI) * (1 - echo * 0.2f);
            // One faint outer rim per echo keeps the fast aftereffect without stacked circular
            // rings.
            {
                float polar = HALF_ARC;
                float thickness = 0.008f + charge * 0.004f;
                for (int slice = 0; slice < SLICES; slice++) {
                    float a = Mth.TWO_PI * slice / SLICES;
                    float b = Mth.TWO_PI * (slice + 1) / SLICES;
                    quad(buffer, matrix, burst, axis, right, up, echoRadius,
                            polar - thickness, Math.min(HALF_ARC, polar + thickness), a, b,
                            (int) ((65 + 35 * charge) * echoFade));
                }
            }
        }
    }

    private static float streakWidth(int streak) {
        return 0.005f + 0.006f * (0.5f + 0.5f * Mth.sin(streak * 2.4f));
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, ActiveBurst burst,
                             Vec3 axis, Vec3 right, Vec3 up, float radius,
                             float inner, float outer, float a, float b, int alpha) {
        if (alpha <= 0 || radius <= 0) return;
        vertex(buffer, matrix, burst.pos, axis, right, up, radius, inner, a, alpha);
        vertex(buffer, matrix, burst.pos, axis, right, up, radius, outer, a, alpha);
        vertex(buffer, matrix, burst.pos, axis, right, up, radius, outer, b, alpha);
        vertex(buffer, matrix, burst.pos, axis, right, up, radius, inner, b, alpha);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 origin, Vec3 axis, Vec3 right, Vec3 up,
                               float radius, float polar, float azimuth, int alpha) {
        Vec3 point = origin.add(axis.scale(radius * Mth.cos(polar)))
                .add(right.scale(radius * Mth.sin(polar) * Mth.cos(azimuth)))
                .add(up.scale(radius * Mth.sin(polar) * Mth.sin(azimuth)));
        buffer.addVertex(matrix, (float) point.x, (float) point.y, (float) point.z).setColor(225, 247, 255, alpha);
    }
}
