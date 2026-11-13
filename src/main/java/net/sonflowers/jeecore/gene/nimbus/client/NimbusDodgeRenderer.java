package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.network.NimbusDodgePayload;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a brief client cloud body and fading echoes without mutating the player entity or model.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class NimbusDodgeRenderer {
    private record Dodge(Vec3 origin, Vec3 direction, float yaw, long started) {}
    private static final Map<UUID, Dodge> DODGES = new HashMap<>();

    public static void receive(NimbusDodgePayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (!payload.active()) { DODGES.remove(payload.playerId()); return; }
        Vec3 direction = payload.motion().lengthSqr() > .0001 ? payload.motion().normalize() : new Vec3(0, 0, 1);
        DODGES.put(payload.playerId(), new Dodge(payload.position(), direction, payload.yaw(), level.getGameTime()));
    }

    public static boolean isDodging(UUID id) {
        var level = Minecraft.getInstance().level;
        var dodge = DODGES.get(id);
        return level != null && dodge != null && level.getGameTime() - dodge.started < 10;
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) DODGES.clear();
    }

    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mc.level == null || DODGES.isEmpty()) return;
        long now = mc.level.getGameTime();
        DODGES.entrySet().removeIf(e -> now - e.getValue().started >= 10);
        if (DODGES.isEmpty()) return;
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 camera = event.getCamera().getPosition();
        Vector3f right = new Vector3f(1, 0, 0).rotate(event.getCamera().rotation());
        Vector3f up = new Vector3f(0, 1, 0).rotate(event.getCamera().rotation());
        Matrix4f matrix = event.getPoseStack().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (var entry : DODGES.entrySet()) {
            var d = entry.getValue();
            var player = mc.level.getPlayerByUUID(entry.getKey());
            float age = now - d.started + partial;
            float fade = Math.max(0, 1 - age / 10);
            // Keep the local first-person view clear; feet particles and sound still signal the
            // dodge.
            if (player == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            Vec3 current = player == null ? d.origin : player.getPosition(partial);
            silhouette(buffer, matrix, current.subtract(camera), right, up, d.yaw, .52f * fade, age);
            for (int echo = 0; echo < 3; echo++) {
                Vec3 origin = d.origin.subtract(d.direction.scale(echo * .40));
                silhouette(buffer, matrix, origin.subtract(camera), right, up, d.yaw,
                        (.30f - echo * .06f) * fade, age + echo * 2);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    static void silhouette(BufferBuilder b, Matrix4f m, Vec3 feet, Vector3f right, Vector3f up,
                                   float yaw, float alpha, float age) {
        double angle = Math.toRadians(yaw), cos = Math.cos(angle), sin = Math.sin(angle);
        double swing = Math.sin(age * .65) * .11;
        // Overlapping soft puffs form a head, torso, arms, and separated legs.
        double[][] puffs = {{0,1.6,0,.23}, {0,1.30,0,.27}, {0,1.05,0,.27}, {0,.82,0,.23},
                {-.34,1.24,swing,.17}, {-.40,.99,swing,.16}, {.34,1.24,-swing,.17}, {.40,.99,-swing,.16},
                {-.15,.58,swing,.18}, {-.16,.31,swing,.17}, {-.16,.10,swing,.16},
                {.15,.58,-swing,.18}, {.16,.31,-swing,.17}, {.16,.10,-swing,.16}};
        for (double[] p : puffs) {
            Vec3 center = feet.add(p[0] * cos - p[2] * sin, p[1], p[0] * sin + p[2] * cos);
            puff(b, m, center, right, up, (float)p[3], alpha);
        }
    }

    private static void puff(BufferBuilder b, Matrix4f m, Vec3 center, Vector3f right, Vector3f up,
                             float radius, float alpha) {
        int a = Math.clamp((int)(alpha * 255), 0, 255);
        for (int i = 0; i < 12; i++) {
            b.addVertex(m, (float)center.x, (float)center.y, (float)center.z).setColor(220, 246, 255, a);
            for (int edge = i; edge <= i + 1; edge++) {
                double theta = edge * Math.PI * 2 / 12;
                float x = radius * (float)Math.cos(theta), y = radius * (float)Math.sin(theta);
                b.addVertex(m, (float)center.x + right.x * x + up.x * y,
                        (float)center.y + right.y * x + up.y * y,
                        (float)center.z + right.z * x + up.z * y).setColor(220, 246, 255, 0);
            }
        }
    }
}
