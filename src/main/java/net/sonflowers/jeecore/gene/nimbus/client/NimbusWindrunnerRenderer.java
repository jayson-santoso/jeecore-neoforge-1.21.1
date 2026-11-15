package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerTrailSprite;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerBurstSprite;
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
 * Renders one angular sprite trail per foot, with diagonal sections through jumps and falls.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class NimbusWindrunnerRenderer {
    private record Step(Vec3 position, Vec3 side, long started, boolean connected) {}
    private record Burst(Vec3 position, Vec3 backward, Vec3 side, long started, int stage) {}
    private static final Map<UUID, Burst> BURSTS = new HashMap<>();
    private static final double BURST_TILT = Math.PI / 3;
    private static final class Trail {
        int stage;
        float displayedStage;
        float speed;
        final double[] phases = new double[2], rates = new double[2];
        Trail(UUID owner) {
            for (int i = 0; i < 2; i++) {
                phases[i] = WindrunnerTrailSprite.phase(owner, i * 2);
                rates[i] = WindrunnerTrailSprite.rate(owner, i * 2);
            }
        }
        Vec3 previous;
        long lastEmission = -1000;
        final ArrayDeque<Step> steps = new ArrayDeque<>();
    }
    private static final Map<UUID, Trail> TRAILS = new HashMap<>();
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID,
            "textures/effect/nimbus_wind_trail.png");
    private static final ResourceLocation BURST_TEXTURE = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID,
            "textures/effect/nimbus_cloudburst.png");

    public static void receive(WindrunnerVisualPayload payload) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int stage = Math.clamp(payload.stage(), 0, 3);
        if (stage == 0) {
            TRAILS.remove(payload.playerId());
            BURSTS.remove(payload.playerId());
            CloudRenderer.clearWindrunnerBurst(payload.playerId());
            return;
        }
        Trail trail = TRAILS.computeIfAbsent(payload.playerId(), Trail::new);
        trail.stage = stage;
        if (trail.displayedStage == 0) trail.displayedStage = stage;
        if (payload.promotion()) {
            Player player = mc.level.getPlayerByUUID(payload.playerId());
            Vec3 forward = payload.motion().multiply(1, 0, 1).normalize();
            if (forward.lengthSqr() < .001) {
                forward = player == null ? new Vec3(0, 0, 1)
                        : Vec3.directionFromRotation(0, player.yBodyRot);
            }
            Vec3 backward = forward.scale(-1);
            Vec3 side = new Vec3(forward.z, 0, -forward.x);
            CloudRenderer.clearWindrunnerBurst(payload.playerId());
            BURSTS.put(payload.playerId(), new Burst(payload.position().add(0, .04, 0),
                    backward, side, mc.level.getGameTime(), stage));
        }
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
        BURSTS.entrySet().removeIf(entry -> now - entry.getValue().started >= WindrunnerBurstSprite.LIFETIME);
        TRAILS.entrySet().removeIf(entry -> {
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null || !player.isAlive()) {
                BURSTS.remove(entry.getKey());
                CloudRenderer.clearWindrunnerBurst(entry.getKey());
                return true;
            }
            Trail trail = entry.getValue();
            trail.displayedStage += Math.clamp(trail.stage - trail.displayedStage, -.125f, .125f);
            trail.steps.removeIf(step -> now - step.started >= 16 + trail.stage * 4);
            Vec3 current = player.position();
            if (trail.previous != null) {
                double distance = current.distanceTo(trail.previous);
                float targetSpeed = distance <= 8 && eligible(player) ? (float)distance : 0;
                trail.speed += (targetSpeed - trail.speed) * .4f;
                if (distance > 8) {
                    BURSTS.remove(entry.getKey());
                    trail.steps.clear();
                    trail.lastEmission = -1000;
                    CloudRenderer.clearWindrunnerBurst(entry.getKey());
                } else if (eligible(player) && (distance > .025 || !player.onGround())) {
                    double yaw = Math.toRadians(player.yBodyRot);
                    Vec3 side = new Vec3(Math.cos(yaw), 0, Math.sin(yaw));
                    boolean connected = !trail.steps.isEmpty()
                            && WindrunnerTrailSprite.connectsRun(now, trail.lastEmission,
                                    current.add(0, .028, 0).distanceTo(trail.steps.getFirst().position));
                    if (!connected) trail.steps.clear();
                    // Keep real foot heights; the renderer joins them with short straight diagonal sections.
                    trail.steps.addFirst(new Step(current.add(0, .028, 0), side, now, connected));
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
            BURSTS.clear();
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
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.08f, 1.08f, 1.08f, 1);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, TEXTURE);
        mc.getTextureManager().getTexture(TEXTURE).setFilter(false, false);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (var entry : TRAILS.entrySet()) {
            Trail trail = entry.getValue();
            if (trail.steps.size() < 2) continue;
            float stopped = WindrunnerTrailSprite.stopOpacity(Math.max(0, now - trail.lastEmission - 3));
            if (stopped < .003f) continue;
            float speed = WindrunnerTrailSprite.speedFactor(trail.speed);
            var player = mc.level.getPlayerByUUID(entry.getKey());
            var history = new ArrayList<>(trail.steps);
            var steps = new ArrayList<Step>();
            // Keep older corners on fixed two-tick samples, so the facets do not slide each tick.
            steps.add(history.getFirst());
            for (int i = 1; i < history.size(); i++) {
                Step point = history.get(i);
                if ((point.started & 1) == 0 || i == history.size() - 1) steps.add(point);
            }
            if (player != null && eligible(player) && trail.lastEmission == mc.level.getGameTime()) {
                Step head = steps.getFirst();
                Vec3 interpolated = player.getPosition(partial);
                steps.set(0, new Step(interpolated.add(0, .028, 0), head.side, head.started, head.connected));
            }
            double available = 0;
            for (int i = 0; i + 1 < steps.size(); i++) available += steps.get(i).position.distanceTo(steps.get(i + 1).position);
            var size = WindrunnerTrailSprite.size(trail.displayedStage);
            for (int foot = -1; foot <= 1; foot += 2) {
                int index = foot == -1 ? 0 : 1;
                var frame = WindrunnerTrailSprite.frame(WindrunnerTrailSprite.frameAt(now, trail.phases[index], trail.rates[index]));
                double length = Math.min(available, size.length() * (.55 + .45 * speed));
                if (length < .06) continue;
                float halfWidth = size.width() * .5f;
                double traveled = 0;
                for (int i = 0; i + 1 < steps.size() && traveled < length; i++) {
                    Step a = steps.get(i), b = steps.get(i + 1);
                    double segment = a.position.distanceTo(b.position);
                    if (segment < .001) continue;
                    if (a.connected && a.position.distanceToSqr(camera) < 130 * 130) {
                        for (int part = 0; part < 3; part++) {
                            double t0 = part / 3.0;
                            double t1 = Math.min((part + 1) / 3.0, (length - traveled) / segment);
                            if (t1 <= t0) break;
                            double u0 = Math.clamp((traveled + segment * t0) / length, 0, 1);
                            double u1 = Math.clamp((traveled + segment * t1) / length, 0, 1);
                            Vec3 start = footPosition(a, b, t0, foot);
                            Vec3 end = footPosition(a, b, t1, foot);
                            float life0 = Math.clamp((float)(1 - (now - (a.started + (b.started - a.started) * t0)) / (16 + trail.stage * 4)), 0, 1);
                            float life1 = Math.clamp((float)(1 - (now - (a.started + (b.started - a.started) * t1)) / (16 + trail.stage * 4)), 0, 1);
                            float strength = .95f * (.55f + .45f * speed) * stopped;
                            int alpha0 = (int)(255 * life0 * strength * WindrunnerTrailSprite.tailOpacity(u0)
                                    * WindrunnerTrailSprite.speedHighlight(u0, now, trail.phases[index]));
                            int alpha1 = (int)(255 * life1 * strength * WindrunnerTrailSprite.tailOpacity(u1)
                                    * WindrunnerTrailSprite.speedHighlight(u1, now, trail.phases[index]));
                            if (Math.max(alpha0, alpha1) < 3) continue;
                            float texU0 = (float)(frame.minU() + (frame.maxU() - frame.minU()) * u0);
                            float texU1 = (float)(frame.minU() + (frame.maxU() - frame.minU()) * u1);
                            float v0 = foot == -1 ? frame.maxV() : frame.minV();
                            float v1 = foot == -1 ? frame.minV() : frame.maxV();
                            // Faint wider copy underneath the crisp core, sharing its frame and tip fade.
                            float lift = halfWidth * 1.4f * .5f;
                            slopedStrip(buffer, matrix, camera, start, end, a.side, foot, Math.PI / 6, lift, halfWidth * 1.4f,
                                    texU0, texU1, v0, v1, (int)(alpha0 * .22f), (int)(alpha1 * .22f));
                            slopedStrip(buffer, matrix, camera, start, end, a.side, foot, Math.PI / 6, lift, halfWidth,
                                    texU0, texU1, v0, v1, alpha0, alpha1);
                        }
                    }
                    traveled += segment;
                }
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        renderBursts(mc, matrix, camera, now);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void renderBursts(Minecraft mc, Matrix4f matrix, Vec3 camera, double now) {
        if (BURSTS.isEmpty()) return;
        // Use the original sprite colors without the trail's overbright multiplier or any tint.
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, BURST_TEXTURE);
        mc.getTextureManager().getTexture(BURST_TEXTURE).setFilter(false, false);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Burst burst : BURSTS.values()) {
            if (burst.position.distanceToSqr(camera) > 130 * 130) continue;
            double age = now - burst.started;
            int alpha = (int)(255 * .95f * WindrunnerBurstSprite.opacity(age));
            if (alpha < 1) continue;
            var frame = WindrunnerBurstSprite.frame(WindrunnerBurstSprite.frameAt(age));
            double expansion = 1 - Math.pow(1 - Math.clamp(age / 6, 0, 1), 3);
            float scale = WindrunnerBurstSprite.scale(burst.stage) * 1.15f;
            double length = (1.75 + .60 * expansion) * scale;
            float halfWidth = (float)(.35 + .10 * expansion) * scale;
            float outerHalfWidth = halfWidth * 1.4f;
            // A short backward drift leaves smoke at the promotion location as the runner moves on.
            Vec3 origin = burst.position.add(burst.backward.scale(.45 * age / WindrunnerBurstSprite.LIFETIME));
            for (int foot = -1; foot <= 1; foot += 2) {
                // Anchor the inner edge beside each leg; smoke fans outward rather than crossing the feet.
                Vec3 start = origin.add(burst.side.scale(foot * (.20 + outerHalfWidth * Math.cos(BURST_TILT))));
                Vec3 end = start.add(burst.backward.scale(length));
                float v0 = foot == -1 ? frame.minV() : frame.maxV();
                float v1 = foot == -1 ? frame.maxV() : frame.minV();
                float lift = (float)(outerHalfWidth * Math.sin(BURST_TILT));
                slopedStrip(buffer, matrix, camera, start, end, burst.side, foot, BURST_TILT, lift,
                        outerHalfWidth, frame.minU(), frame.maxU(), v0, v1, (int)(alpha * .22f), (int)(alpha * .22f));
                slopedStrip(buffer, matrix, camera, start, end, burst.side, foot, BURST_TILT, lift,
                        halfWidth, frame.minU(), frame.maxU(), v0, v1, alpha, alpha);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
    }

    private static Vec3 footPosition(Step a, Step b, double t, int foot) {
        // Follow actual turns with short straight strokes; no spline overshoot or fluid curling.
        Vec3 start = a.position.add(a.side.scale(foot * .20));
        Vec3 end = b.position.add(b.side.scale(foot * .20));
        return start.lerp(end, t);
    }

    static void slopedStrip(BufferBuilder buffer, Matrix4f matrix, Vec3 camera, Vec3 a, Vec3 b, Vec3 fallback,
                                  int foot, double tilt, float lift, float halfWidth, float u0, float u1, float v0, float v1,
                                  int alphaA, int alphaB) {
        Vec3 direction = b.subtract(a).normalize();
        if (direction.lengthSqr() < .0001) return;
        Vec3 side = new Vec3(-direction.z, 0, direction.x).normalize();
        // A straight vertical jump still needs a visible strip rather than a zero-width normal.
        if (side.lengthSqr() < .0001) side = fallback;
        if (side.dot(fallback) < 0) side = side.scale(-1);
        // Raise each outer edge into a mirrored V, relative to the path's slope.
        Vec3 up = direction.cross(side).normalize();
        if (up.y < 0) up = up.scale(-1);
        side = side.scale(Math.cos(tilt)).add(up.scale(foot * Math.sin(tilt)));
        // Both layers share a center lift so the wider underlay's inner edge clears the floor.
        // Preserve endpoint height differences on ascent/descent.
        // A tiny longitudinal overlap closes seams where the faceted sections meet.
        Vec3 overlap = direction.scale(.004);
        Vec3 start = a.subtract(overlap).add(up.scale(lift));
        Vec3 end = b.add(overlap).add(up.scale(lift));
        vertex(buffer, matrix, start.subtract(side.scale(halfWidth)).subtract(camera), u0, v0, alphaA);
        vertex(buffer, matrix, end.subtract(side.scale(halfWidth)).subtract(camera), u1, v0, alphaB);
        vertex(buffer, matrix, end.add(side.scale(halfWidth)).subtract(camera), u1, v1, alphaB);
        vertex(buffer, matrix, start.add(side.scale(halfWidth)).subtract(camera), u0, v1, alphaA);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f matrix, Vec3 p, float u, float v, int alpha) {
        buffer.addVertex(matrix, (float)p.x, (float)p.y, (float)p.z).setUv(u, v).setColor(255, 255, 255, alpha);
    }
}
