package net.sonflowers.jeecore.gene.dryad.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.dryad.network.SporeVfxPayload;
import net.sonflowers.jeecore.gene.dryad.entity.DryadSporeEntity;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Maintains transient client spore visuals and the local guard overlay from server payloads.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class SporeRingRenderer {
    private static final ResourceLocation INNER = ResourceLocation.fromNamespaceAndPath(
            JeeCore.MOD_ID, "textures/effects/dryad_heal_ring_0.png");
    private static final ResourceLocation OUTER = ResourceLocation.fromNamespaceAndPath(
            JeeCore.MOD_ID, "textures/effects/spore_ring.png");
    private static final int BURST_TICKS = 14;
    private static final int ENTER_TICKS = 6;
    private static final int EXIT_TICKS = 8;
    private static final DustParticleOptions LANDED_DUST = new DustParticleOptions(new Vector3f(0.72f, 1.0f, 0.80f), 0.45f);
    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final List<Spark> SPARKS = new ArrayList<>();
    private static final Map<UUID, GuardRing> GUARDS = new HashMap<>();
    private static ClientLevel currentLevel;

    private record Burst(Vec3 position, double started) {}
    private record Ring(Vec3 position, float radius, float opacity, float phase) {}
    private record Column(Vec3 position, float radius, float height, float opacity, float phase) {}
    private record Spark(int entityId, UUID owner, Vec3 position, Vec3 velocity, double started,
                         float size, float phase) {}
    private static final class GuardRing {
        Vec3 position;
        final double started;
        double expiry;
        double ending = -1;

        GuardRing(Vec3 position, double now, int duration) {
            this.position = position;
            started = now;
            expiry = now + duration;
        }
    }

    private SporeRingRenderer() {}

    private static ClientLevel level() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != currentLevel) {
            BURSTS.clear();
            SPARKS.clear();
            GUARDS.clear();
            currentLevel = level;
        }
        return level;
    }

    public static void receive(SporeVfxPayload payload) {
        ClientLevel level = level();
        if (level == null) return;
        double now = level.getGameTime();
        if (payload.kind() == SporeVfxPayload.RELEASE) {
            BURSTS.add(new Burst(payload.position(), now));
        } else if (payload.kind() == SporeVfxPayload.GUARD && payload.duration() > 0) {
            GuardRing previous = GUARDS.get(payload.playerId());
            if (previous != null && previous.ending < 0) {
                // Refresh duration without restarting the entry animation.
                previous.expiry = now + payload.duration();
                previous.position = payload.position();
            } else {
                GUARDS.put(payload.playerId(), new GuardRing(payload.position(), now, payload.duration()));
            }
        } else if (payload.kind() == SporeVfxPayload.END) {
            GuardRing previous = GUARDS.get(payload.playerId());
            if (previous != null && previous.ending < 0) previous.ending = now;
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = level();
        if (level == null) return;
        double now = level.getGameTime();
        BURSTS.removeIf(burst -> now - burst.started >= BURST_TICKS);
        SPARKS.removeIf(spark -> now - spark.started >= 10
                || !(level.getEntity(spark.entityId) instanceof DryadSporeEntity spore)
                || !spore.getUUID().equals(spark.owner) || spore.isRemoved() || spore.isBlinkHidden());
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof DryadSporeEntity spore) || spore.isRemoved() || spore.isBlinkHidden()
                    || Minecraft.getInstance().player == null
                    || spore.distanceToSqr(Minecraft.getInstance().player) > 32 * 32) continue;
            if (spore.hasLanded() && (level.getGameTime() + (spore.getUUID().hashCode() & 15)) % 12 == 0) {
                float dustAngle = level.random.nextFloat() * Mth.TWO_PI;
                double radius = 0.20 + level.random.nextDouble() * 0.12;
                level.addParticle(LANDED_DUST, spore.getX() + Mth.cos(dustAngle) * radius,
                        spore.getY() + 0.08, spore.getZ() + Mth.sin(dustAngle) * radius,
                        Mth.cos(dustAngle) * 0.004, 0.008, Mth.sin(dustAngle) * 0.004);
            }
            if ((level.getGameTime() + (spore.getUUID().hashCode() & 3)) % 4 != 0) continue;
            // One short-lived, fast mote every four ticks: at most three per pod.
            float angle = level.random.nextFloat() * Mth.TWO_PI;
            float speed = 0.045f + level.random.nextFloat() * 0.035f;
            Vec3 position = spore.position().add(Mth.cos(angle) * 0.18,
                    0.18 + level.random.nextFloat() * 0.16, Mth.sin(angle) * 0.18);
            Vec3 velocity = new Vec3(Mth.cos(angle) * speed,
                    0.03 + level.random.nextFloat() * 0.04, Mth.sin(angle) * speed);
            SPARKS.add(new Spark(spore.getId(), spore.getUUID(), position, velocity, now,
                    0.009f + level.random.nextFloat() * 0.005f, angle));
        }
        GUARDS.entrySet().removeIf(entry -> {
            GuardRing ring = entry.getValue();
            var player = level.getPlayerByUUID(entry.getKey());
            if (player != null) ring.position = player.position();
            if (ring.ending < 0 && (now >= ring.expiry
                    || (now - ring.started > 2 && (player == null || !player.isAlive())))) {
                ring.ending = now;
            }
            if (player == Minecraft.getInstance().player && player != null && ring.ending < 0
                    && now - ring.started > 2 && !player.hasEffect(ModMobEffects.SPORE_GUARD)) ring.ending = now;
            return ring.ending >= 0 && now - ring.ending >= EXIT_TICKS;
        });
    }

    public static float localOverlayFade(float partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return 0;
        var effect = mc.player.getEffect(ModMobEffects.SPORE_GUARD);
        GuardRing ring = GUARDS.get(mc.player.getUUID());
        if (ring != null && ring.ending >= 0) {
            // Natural expiry already faded the overlay during its final ticks. Only early removal
            // needs an additional exit fade.
            if (ring.ending >= ring.expiry - 1) return 0;
            return 1 - ease((float) ((mc.level.getGameTime() + partialTick - ring.ending) / EXIT_TICKS));
        }
        if (effect == null) return 0;
        float enter = ring == null ? 1 : ease((float) ((mc.level.getGameTime() + partialTick - ring.started) / ENTER_TICKS));
        float exit = effect.isInfiniteDuration() ? 1 : Mth.clamp(effect.getDuration() / (float) EXIT_TICKS, 0, 1);
        return enter * exit;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        ClientLevel level = level();
        if (level == null) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double now = level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        List<Ring> rings = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        for (Burst burst : BURSTS) {
            float progress = Mth.clamp((float) ((now - burst.started) / BURST_TICKS), 0, 1);
            Vec3 ground = ground(level, burst.position);
            if (ground != null && ground.distanceToSqr(camera) < 64 * 64) {
                rings.add(new Ring(ground, 1.1f * ease(progress), (1 - progress) * (1 - progress), 0));
            }
        }
        for (var entry : GUARDS.entrySet()) {
            GuardRing ring = entry.getValue();
            var player = level.getPlayerByUUID(entry.getKey());
            if (player == null) continue;
            Vec3 feet = player.getPosition(partial);
            Vec3 ground = ground(level, feet);
            // Keep the aura visible in the air, using nearby ground when available.
            if (ground == null) ground = feet.add(0, 0.025, 0);
            if (ground.distanceToSqr(camera) >= 64 * 64) continue;
            float enter = ease((float) ((now - ring.started) / ENTER_TICKS));
            float exit = ring.ending < 0 ? 0 : ease((float) ((now - ring.ending) / EXIT_TICKS));
            float phase = phase(entry.getKey());
            float opacity = enter * (1 - exit);
            float radius = 0.85f * enter * (1 + 0.2f * exit);
            rings.add(new Ring(ground, radius, opacity, phase));
            float height = 0.85f + 0.15f * Mth.sin((float) (now * 0.13) + phase + 0.7f);
            columns.add(new Column(ground, radius * 0.86f, height * enter * (1 - exit), opacity, phase));
        }
        for (var entity : level.entitiesForRendering()) {
            if (!(entity instanceof DryadSporeEntity spore) || spore.isRemoved() || spore.isBlinkHidden()) continue;
            Vec3 base = ground(level, spore.getPosition(partial));
            if (base == null || base.distanceToSqr(camera) > 32 * 32) continue;
            float fade = Mth.clamp((DryadSporeEntity.LIFETIME_TICKS - spore.getSporeAge() - partial) / 40, 0.2f, 1);
            // About alpha 8/255 at the base, falling to zero over a short column.
            columns.add(new Column(base, 0.29f, 0.45f, fade * 0.16f, phase(spore.getUUID())));
        }
        if (rings.isEmpty() && columns.isEmpty() && SPARKS.isEmpty()) return;
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        try {
            if (!rings.isEmpty()) {
                drawPass(matrix, camera, rings, INNER, true, now);
                drawPass(matrix, camera, rings, OUTER, false, now);
            }
            drawColumns(matrix, camera, columns, now);
            drawSparks(matrix, camera, now);
        } finally {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.disableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private static Vec3 ground(ClientLevel level, Vec3 feet) {
        var hit = level.clip(new ClipContext(feet.add(0, 0.3, 0), feet.add(0, -6, 0),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        return hit.getType() == HitResult.Type.BLOCK ? new Vec3(feet.x, hit.getLocation().y + 0.025, feet.z) : null;
    }

    private static void drawPass(Matrix4f matrix, Vec3 camera, List<Ring> rings, ResourceLocation texture, boolean inner, double now) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Ring ring : rings) {
            Vec3 position = ring.position.subtract(camera);
            // Independent periods and phases keep the inner glow and outer outline breathing apart.
            float haloPulse = 1 + 0.065f * Mth.sin((float) (now * 0.17) + ring.phase);
            float innerPulse = 1 + 0.035f * Mth.sin((float) (now * 0.09) + ring.phase + 1.3f);
            float outerPulse = 1 + 0.05f * Mth.sin((float) (now * 0.12) + ring.phase + 3.1f);
            if (inner) {
                quad(buffer, matrix, position, ring.radius * 0.95f * haloPulse, 145, 255, 175, (int) (36 * ring.opacity));
                quad(buffer, matrix, position.add(0, 0.004, 0), ring.radius * 0.70f * innerPulse,
                        220, 255, 232, (int) (136 * ring.opacity));
            } else {
                quad(buffer, matrix, position.add(0, 0.008, 0), ring.radius * 1.06f * haloPulse,
                        190, 255, 205, (int) (44 * ring.opacity));
                quad(buffer, matrix, position.add(0, 0.012, 0), ring.radius * outerPulse,
                        235, 255, 240, (int) (190 * ring.opacity));
            }
        }
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static float phase(UUID id) {
        return (id.hashCode() & 65535) / 65535f * Mth.TWO_PI;
    }

    private static void drawSparks(Matrix4f matrix, Vec3 camera, double now) {
        if (SPARKS.isEmpty()) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Spark spark : SPARKS) {
            float age = (float) (now - spark.started);
            if (age < 0 || age >= 10) continue;
            Vec3 world = spark.position.add(spark.velocity.scale(age)).add(0, 0.001 * age * age, 0);
            Vec3 pos = world.subtract(camera);
            if (pos.lengthSqr() > 32 * 32) continue;
            float twinkle = 0.5f + 0.5f * Mth.sin(age * 2.2f + spark.phase);
            float size = spark.size * (0.7f + twinkle * 0.65f);
            int alpha = (int) (230 * (1 - age / 10) * (0.45f + 0.55f * twinkle));
            drawSpark(buffer, matrix, pos, Vec3.ZERO, size, alpha);
        }
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    static void drawSpark(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, Vec3 camera, float size, int alpha) {
        Vec3 facing = camera.subtract(pos).normalize();
        Vec3 right = facing.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 0.001 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(facing).normalize();
        sparkQuad(buffer, matrix, pos, right, up, size * 2.2f, size * 2.2f, 185, 255, 205, alpha / 4);
        sparkQuad(buffer, matrix, pos, right, up, size * 1.7f, size * 0.18f, 205, 255, 220, alpha / 2);
        sparkQuad(buffer, matrix, pos, right, up, size * 0.18f, size * 1.7f, 205, 255, 220, alpha / 2);
        sparkQuad(buffer, matrix, pos, right, up, size * 0.5f, size * 0.5f, 250, 255, 252, alpha);
    }

    private static void sparkQuad(VertexConsumer buffer, Matrix4f matrix, Vec3 pos, Vec3 right, Vec3 up,
                                  float width, float height, int r, int g, int b, int alpha) {
        Vec3 side = right.scale(width), vertical = up.scale(height);
        for (Vec3 corner : List.of(pos.subtract(side).subtract(vertical), pos.add(side).subtract(vertical),
                pos.add(side).add(vertical), pos.subtract(side).add(vertical))) {
            buffer.addVertex(matrix, (float) corner.x, (float) corner.y, (float) corner.z).setColor(r, g, b, alpha);
        }
    }

    private static void drawColumns(Matrix4f matrix, Vec3 camera, List<Column> columns, double now) {
        if (columns.isEmpty()) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (Column column : columns) {
            Vec3 pos = column.position.subtract(camera);
            float radius = column.radius * (1 + 0.025f * Mth.sin((float) (now * 0.11) + column.phase));
            int alpha = (int) (52 * column.opacity * (0.92f + 0.08f * Mth.sin((float) (now * 0.14) + column.phase)));
            for (int i = 0; i < 48; i++) {
                float a = i * Mth.TWO_PI / 48, b = (i + 1) * Mth.TWO_PI / 48;
                float x1 = (float) pos.x + Mth.cos(a) * radius, z1 = (float) pos.z + Mth.sin(a) * radius;
                float x2 = (float) pos.x + Mth.cos(b) * radius, z2 = (float) pos.z + Mth.sin(b) * radius;
                float y = (float) pos.y;
                buffer.addVertex(matrix, x1, y, z1).setColor(185, 255, 202, alpha);
                buffer.addVertex(matrix, x2, y, z2).setColor(185, 255, 202, alpha);
                buffer.addVertex(matrix, x2, y + column.height, z2).setColor(185, 255, 202, 0);
                buffer.addVertex(matrix, x1, y + column.height, z1).setColor(185, 255, 202, 0);
            }
        }
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, float radius, int r, int g, int b, int alpha) {
        if (radius < 0.01f || alpha <= 0) return;
        float x = (float) pos.x, y = (float) pos.y, z = (float) pos.z;
        buffer.addVertex(matrix, x - radius, y, z - radius).setUv(0, 0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x - radius, y, z + radius).setUv(0, 1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + radius, y, z + radius).setUv(1, 1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, x + radius, y, z - radius).setUv(1, 0).setColor(r, g, b, alpha);
    }

    private static float ease(float progress) {
        return Mth.sin(Mth.clamp(progress, 0, 1) * Mth.HALF_PI);
    }
}
