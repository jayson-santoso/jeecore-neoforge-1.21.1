package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.network.WindrunnerVisualPayload;
import net.sonflowers.jeecore.gene.nimbus.server.CloudManager;
import org.joml.Matrix4f;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders paired client foot trails from a bounded history of actual movement positions.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class NimbusWindrunnerRenderer {
    private record Step(Vec3 position, Vec3 side, long started, boolean connected) {}
    private static final class Trail {
        int stage;
        Vec3 previous;
        long lastEmission = -1000;
        final ArrayDeque<Step> steps = new ArrayDeque<>();
    }
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();

    public static void receive(WindrunnerVisualPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int stage = Math.clamp(payload.stage(), 0, 3);
        if (stage == 0) {
            TRAILS.remove(payload.playerId());
            CloudRenderer.clearWindrunnerBurst(payload.playerId());
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(payload.playerId(), id -> new Trail());
        trail.stage = stage;
        if (payload.promotion()) CloudRenderer.spawnWindrunnerBurst(payload.playerId(), payload.position(), payload.motion(), stage);
    }

    private static boolean eligible(Player p) {
        return p.isAlive() && !p.isSpectator() && !p.isPassenger() && !p.getAbilities().flying
                && !p.isFallFlying() && !p.isInWater() && !p.isInLava() && !p.onClimbable()
                && !CloudManager.isClouded(p.getUUID()) && !NimbusDodgeRenderer.isDodging(p.getUUID());
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        long now = mc.level.getGameTime();
        TRAILS.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null || !player.isAlive()) {
                CloudRenderer.clearWindrunnerBurst(entry.getKey());
                return true;
            }
            Trail trail = entry.getValue();
            trail.steps.removeIf(step -> now - step.started >= 16 + trail.stage * 4);
            Vec3 current = player.position();
            if (trail.previous != null) {
                double distance = current.distanceTo(trail.previous);
                if (distance > 8) {
                    trail.steps.clear();
                    trail.lastEmission = -1000;
                    CloudRenderer.clearWindrunnerBurst(entry.getKey());
                } else if (eligible(player) && current.subtract(trail.previous).horizontalDistance() > .08) {
                    double yaw = Math.toRadians(player.yBodyRot);
                    Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
                    trail.steps.addFirst(new Step(current.add(0, .07, 0), side, now, trail.lastEmission == now - 1));
                    trail.lastEmission = now;
                    while (trail.steps.size() > 30) trail.steps.removeLast();
                }
            }
            trail.previous = current;
            return false;
        });
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            TRAILS.clear();
            CloudRenderer.clearWindrunnerBursts();
        }
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || TRAILS.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double now = mc.level.getGameTime() + partial;
        Vec3 camera = event.getCamera().getPosition();
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var entry : TRAILS.entrySet()) {
            Trail trail = entry.getValue();
            if (trail.steps.size() < 2) continue;
            var player = mc.level.getPlayerByUUID(entry.getKey());
            var steps = new ArrayList<>(trail.steps);
            if (player != null && trail.lastEmission == mc.level.getGameTime()) {
                Step head = steps.getFirst();
                steps.set(0, new Step(player.getPosition(partial).add(0, .07, 0), head.side, head.started, head.connected));
            }
            double length = 3 + trail.stage * 1.2;
            double traveled = 0;
            for (int i = 0; i + 1 < steps.size() && traveled < length; i++) {
                Step a = steps.get(i), b = steps.get(i + 1);
                double segment = a.position.distanceTo(b.position);
                if (segment < .001) continue;
                if (a.connected && a.position.distanceToSqr(camera) < 130 * 130) {
                    Vec3 p0 = i > 0 && steps.get(i - 1).connected ? steps.get(i - 1).position : a.position;
                    Vec3 p3 = i + 2 < steps.size() && b.connected ? steps.get(i + 2).position : b.position;
                    for (int foot = -1; foot <= 1; foot += 2) {
                        for (int strand = 0; strand < 2; strand++) {
                            // Connected pixel sections retain distinct, stepped thickness without
                            // gaps.
                            double cellLength = .20;
                            int firstCell = (int)Math.floor(traveled / cellLength);
                            int lastCell = (int)Math.ceil(Math.min(length, traveled + segment) / cellLength);
                            for (int cell = firstCell; cell < lastCell; cell++) {
                                double u = Math.clamp((cell + .5) * cellLength / length, 0, 1);
                                double cellStart = cell * cellLength;
                                double cellEnd = (cell + 1) * cellLength;
                                double startDistance = Math.max(traveled, cellStart);
                                double endDistance = Math.min(Math.min(traveled + segment, cellEnd), length);
                                if (endDistance <= startDistance) continue;
                                double t0 = (startDistance - traveled) / segment;
                                double t1 = (endDistance - traveled) / segment;
                                Vec3 start = wisp(p0, a, b, p3, t0, startDistance / length, now, foot, strand, trail.stage);
                                Vec3 end = wisp(p0, a, b, p3, t1, endDistance / length, now, foot, strand, trail.stage);
                                double midpoint = (t0 + t1) * .5;
                                float life = Math.clamp((float)(1 - (now - (a.started + (b.started - a.started) * midpoint))
                                        / (16 + trail.stage * 4)), 0, 1);
                                float baseWidth = (.016f + trail.stage * .008f) * (strand == 0 ? 1 : .55f);
                                // A toned-down foot root halves in width before narrowing to the
                                // thin tail.
                                float band = u < .40 ? 3f : u < .65 ? 1.5f : u < .82 ? .75f : u < .94 ? .18f : .035f;
                                float pixel = 1f / 256;
                                float width = Math.max(pixel, Math.round(baseWidth * band / pixel) * pixel);
                                float strength = strand == 0 ? 1 : .45f;
                                int alpha = Math.round((float)(156 * life * Math.pow(1 - u, 1.45) * strength) / 12) * 12;
                                if (alpha < 12) continue;
                                ribbon(buffer, matrix, camera, start, end, a.side, width, width, alpha, alpha);
                            }
                        }
                    }
                }
                traveled += segment;
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static Vec3 wisp(Vec3 p0, Step a, Step b, Vec3 p3, double t, double u, double now,
                             int foot, int strand, int stage) {
        double t2 = t * t, t3 = t2 * t;
        Vec3 center = a.position.scale(2).add(b.position.subtract(p0).scale(t))
                .add(p0.scale(2).subtract(a.position.scale(5)).add(b.position.scale(4)).subtract(p3).scale(t2))
                .add(p0.scale(-1).add(a.position.scale(3)).subtract(b.position.scale(3)).add(p3).scale(t3)).scale(.5);
        Vec3 side = a.side.lerp(b.side, t).normalize();
        double swell = Math.sin(Math.PI * u);
        double phase = u * 13 - now * .22 + foot * 1.9 + strand * 2.1;
        double sideways = foot * (.14 + strand * .035) + Math.sin(phase) * (.055 + stage * .018) * swell;
        double lift = (.025 + .045 * Math.sin(phase * .8)) * swell;
        // Small stepped offsets reinforce the pixel-like silhouette while retaining the flowing
        // path.
        sideways = Math.rint(sideways * 32) / 32;
        lift = Math.rint(lift * 64) / 64;
        return center.add(side.scale(sideways)).add(0, lift, 0);
    }

    private static void ribbon(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 a, Vec3 b, Vec3 fallback,
                               float widthA, float widthB, int alphaA, int alphaB) {
        // Tiny end overlap closes camera-facing seams at corners and thickness changes.
        Vec3 overlap = b.subtract(a).normalize().scale(.006);
        a = a.subtract(overlap);
        b = b.add(overlap);
        Vec3 normal = b.subtract(a).cross(camera.subtract(a)).normalize();
        if (normal.lengthSqr() < .001) normal = fallback;
        vertex(buffer, matrix, a.add(normal.scale(widthA)).subtract(camera), alphaA);
        vertex(buffer, matrix, b.add(normal.scale(widthB)).subtract(camera), alphaB);
        vertex(buffer, matrix, b.subtract(normal.scale(widthB)).subtract(camera), alphaB);
        vertex(buffer, matrix, a.subtract(normal.scale(widthA)).subtract(camera), alphaA);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, int alpha) {
        buffer.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setColor(224, 245, 255, alpha);
    }
}
