package net.sonflowers.jeecore.gene.infernal.client;

import net.sonflowers.jeecore.gene.nimbus.client.WindRenderer;

import com.mojang.blaze3d.vertex.BufferBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders client ignition arming, smoke, and detonation effects for local and remote players.
 */
public class InfernalChargeVfxRenderer {
    private static final Map<Integer, ActiveCharge> ACTIVE_CHARGES = new ConcurrentHashMap<>();
    private static final Map<UUID, RemoteArmedState> REMOTE_ARMED_STATES = new ConcurrentHashMap<>();
    private static final List<IgnitionParticle> PARTICLES = new ArrayList<>();
    private static final List<HeadVentParticle> HEAD_VENT_PARTICLES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    private static final int ACTIVE_TIMEOUT_TICKS = 150;
    private static final int FADE_TICKS = 18;
    public static float screenShakeIntensity = 0.0f;
    public static float flashIntensity = 0.0f;
    public static float redFlashIntensity = 0.0f;
    public static float whiteFlashIntensity = 0.0f;
    private static boolean wasLocallyArmed = false;
    private static long lastHeadVentTick = Long.MIN_VALUE;
    private static net.minecraft.client.multiplayer.ClientLevel headVentLevel;
    private static final int MAX_HEAD_VENT_PARTICLES = 1600;

    private record RemoteArmedState(int timer, int maxTimer, long updatedAtMillis) {
    }

    private static class ActiveCharge {
        final int entityId;
        Vec3 pos;
        final double startGameTime;
        final double timeoutGameTime;
        double fadeStartGameTime = -1.0;

        ActiveCharge(int entityId, Vec3 pos, double gameTime) {
            this.entityId = entityId;
            this.pos = pos;
            this.startGameTime = gameTime;
            this.timeoutGameTime = gameTime + ACTIVE_TIMEOUT_TICKS;
        }

        boolean isFading() {
            return fadeStartGameTime >= 0.0;
        }

        void startFade(double gameTime) {
            if (!isFading()) {
                fadeStartGameTime = gameTime;
            }
        }

        float visibility(double gameTime) {
            if (!isFading()) return 1.0f;
            return (float) Math.max(0.0, 1.0 - (gameTime - fadeStartGameTime) / FADE_TICKS);
        }
    }

    private static class IgnitionParticle {
        double x, y, z, vx, vy, vz;
        double prevX, prevY, prevZ;
        int age, maxAge;
        float size;
        int colorSet;

        IgnitionParticle(Vec3 pos, Vec3 motion, boolean plasma) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.maxAge = plasma ? 18 + RANDOM.nextInt(10) : 10 + RANDOM.nextInt(10);
            this.size = plasma ? 0.105f + RANDOM.nextFloat() * 0.075f : 0.045f + RANDOM.nextFloat() * 0.045f;
            this.colorSet = plasma ? 1 : RANDOM.nextInt(3);
        }

        boolean tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
            x += vx;
            y += vy;
            z += vz;
            vy += 0.004;
            vx *= 0.91;
            vy *= 0.91;
            vz *= 0.91;
            return ++age < maxAge;
        }
    }

    private static class HeadVentParticle {
        double x, y, z, prevX, prevY, prevZ, vx, vy, vz;
        int age, maxAge;
        float width, height, phase, flutter;
        final boolean smoke;
        float heat;
        int colorVariant;

        HeadVentParticle(Vec3 pos, Vec3 motion, float heat, boolean smoke) {
            this.x = this.prevX = pos.x;
            this.y = this.prevY = pos.y;
            this.z = this.prevZ = pos.z;
            this.vx = motion.x;
            this.vy = motion.y;
            this.vz = motion.z;
            this.heat = heat;
            this.smoke = smoke;
            this.phase = RANDOM.nextFloat() * (float) Math.PI * 2.0f;
            this.flutter = 0.35f + RANDOM.nextFloat() * 0.55f;
            this.colorVariant = RANDOM.nextInt(4);
            this.maxAge = smoke ? 28 + RANDOM.nextInt(22) : 10 + RANDOM.nextInt(15);
            float size = smoke ? 0.10f + RANDOM.nextFloat() * 0.15f
                    : (0.035f + RANDOM.nextFloat() * 0.085f) * (1.0f + heat * 0.8f);
            this.width = size * (0.45f + RANDOM.nextFloat() * 1.15f);
            this.height = size * (0.65f + RANDOM.nextFloat() * 1.9f);
        }

        boolean tick() {
            prevX = x;
            prevY = y;
            prevZ = z;
            x += vx;
            y += vy;
            z += vz;
            double turbulence = smoke ? 0.002 : 0.008 + heat * 0.014;
            vx = vx * 0.91 + Math.sin(phase + age * flutter) * turbulence;
            vy = vy * 0.94 + (smoke ? 0.002 : 0.006 + heat * 0.006);
            vz = vz * 0.91 + Math.cos(phase + age * flutter * 0.8) * turbulence;
            return ++age < maxAge;
        }
    }

    public static void prime(int entityId, Vec3 pos) {
        var mc = Minecraft.getInstance();
        double gameTime = mc.level != null ? mc.level.getGameTime() : 0.0;
        ACTIVE_CHARGES.put(entityId, new ActiveCharge(entityId, pos, gameTime));
        HEAD_VENT_PARTICLES.clear();
        wasLocallyArmed = false;
        spawnParticleBurst(pos, 18, 0.38);
        triggerCamera(pos, 1.35f, 0.0f, 2.35f);
    }

    public static void detonate(int entityId, Vec3 pos, boolean timeout) {
        ActiveCharge charge = ACTIVE_CHARGES.remove(entityId);
        Vec3 burstPos = charge != null ? charge.pos : pos;
        spawnParticleBurst(burstPos, 42, 0.72);
        triggerCamera(burstPos, 1.05f, 0.0f, 0.0f);
        if (timeout) {
            whiteFlashIntensity = Math.max(whiteFlashIntensity, 1.0f);
        } else {
            // For successful QTE hit, flash red then white with longer duration.
            redFlashIntensity = Math.max(redFlashIntensity, 1.2f);
            whiteFlashIntensity = Math.max(whiteFlashIntensity, 0.8f);
        }
    }

    public static void setRemoteArmedState(UUID playerUUID, boolean armed, int timer, int maxTimer) {
        if (armed) {
            REMOTE_ARMED_STATES.put(playerUUID, new RemoteArmedState(timer, Math.max(1, maxTimer), System.currentTimeMillis()));
        } else {
            REMOTE_ARMED_STATES.remove(playerUUID);
        }
    }

    public static boolean hasBursts() {
        return isLocalIgnitionArmed() || !REMOTE_ARMED_STATES.isEmpty()
                || !ACTIVE_CHARGES.isEmpty() || !PARTICLES.isEmpty() || !HEAD_VENT_PARTICLES.isEmpty();
    }

    public static void render(BufferBuilder buffer, Matrix4f matrix, double gameTime) {
        var mc = Minecraft.getInstance();
        if (headVentLevel != mc.level) {
            HEAD_VENT_PARTICLES.clear();
            REMOTE_ARMED_STATES.clear();
            wasLocallyArmed = false;
            lastHeadVentTick = Long.MIN_VALUE;
            headVentLevel = mc.level;
        }
        if (mc.level != null && !mc.isPaused() && lastHeadVentTick != mc.level.getGameTime()) {
            lastHeadVentTick = mc.level.getGameTime();
            HEAD_VENT_PARTICLES.removeIf(p -> !p.tick());
            tickLocalArmedCharge();
            tickRemoteArmedCharges();
        }
        tickActiveCharges(buffer, matrix, gameTime);
        tickParticles(buffer, matrix);
        tickHeadVentParticles(buffer, matrix);
    }

    public static void tickLocalArmedCharge() {
        var mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || mc.isPaused()) return;

        boolean armed = isLocalIgnitionArmed();
        if (!armed) {
            if (wasLocallyArmed) {
                HEAD_VENT_PARTICLES.clear();
            }
            wasLocallyArmed = false;
            return;
        }

        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        wasLocallyArmed = true;
        float heat = Math.min(1.0f, data.getIgnitionTimer() / (float) data.getMaxIgnitionTimer());
        spawnHeadVent(player, heat);

        // Spawn campfire smoke particles at player's feet for ticking state.
        if (mc.level.getGameTime() % 4 == 0) {
            spawnTickingStateSmoke(player);
        }
    }

    private static void spawnTickingStateSmoke(Player player) {
        if (!(player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                player.getX(), player.getY() + 0.1, player.getZ(),
                3, 0.3, 0.0, 0.3, 0.03);
    }

    private static void tickRemoteArmedCharges() {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        UUID localId = mc.player != null ? mc.player.getUUID() : null;
        long now = System.currentTimeMillis();

        REMOTE_ARMED_STATES.entrySet().removeIf(entry -> {
            if (now - entry.getValue().updatedAtMillis() > 500L) return true;
            if (entry.getKey().equals(localId)) return false;
            Player player = null;
            for (Player candidate : mc.level.players()) {
                if (candidate.getUUID().equals(entry.getKey())) {
                    player = candidate;
                    break;
                }
            }
            if (player == null || !player.isAlive()) return true;
            float heat = Math.min(1.0f, entry.getValue().timer() / (float) entry.getValue().maxTimer());
            spawnHeadVent(player, heat);
            return false;
        });
    }

    private static void spawnHeadVent(Player player, float heat) {
        heat = Math.max(0.0f, Math.min(1.0f, heat));
        int count = 5 + (int) (heat * 9.0f) + RANDOM.nextInt(4);
        if (heat > 0.65f && RANDOM.nextInt(4) == 0) count += 8;
        Vec3 head = player.getEyePosition().add(0.0, 0.16, 0.0);
        Vec3 movement = new Vec3(player.getX() - player.xo, player.getY() - player.yo, player.getZ() - player.zo);
        for (int i = 0; i < count; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2.0;
            double spread = 0.04 + RANDOM.nextDouble() * (0.16 + heat * 0.25);
            Vec3 radial = new Vec3(Math.cos(angle) * spread, 0.0, Math.sin(angle) * spread);
            Vec3 offset = new Vec3(
                    radial.x,
                    RANDOM.nextDouble() * (0.22 + heat * 0.18),
                    radial.z
            );
            Vec3 motion = new Vec3(
                    -movement.x * 0.18 + radial.x * 0.15 + (RANDOM.nextDouble() - 0.5) * (0.06 + heat * 0.13),
                    0.035 + heat * 0.07 + RANDOM.nextDouble() * 0.09,
                    -movement.z * 0.18 + radial.z * 0.15 + (RANDOM.nextDouble() - 0.5) * (0.06 + heat * 0.13)
            );
            if (HEAD_VENT_PARTICLES.size() >= MAX_HEAD_VENT_PARTICLES) break;
            HEAD_VENT_PARTICLES.add(new HeadVentParticle(head.add(offset), motion, heat, false));
        }
        float smokeBuild = Math.max(0.0f, (heat - 0.6f) / 0.4f);
        int smokeCount = (int) (smokeBuild * 2) + (RANDOM.nextFloat() < smokeBuild * 0.6f ? 1 : 0);
        for (int i = 0; i < smokeCount && HEAD_VENT_PARTICLES.size() < MAX_HEAD_VENT_PARTICLES; i++) {
            Vec3 offset = new Vec3((RANDOM.nextDouble() - 0.5) * 1.25,
                    0.25 + RANDOM.nextDouble() * 0.5, (RANDOM.nextDouble() - 0.5) * 1.25);
            Vec3 motion = new Vec3(offset.x * 0.045 + (RANDOM.nextDouble() - 0.5) * 0.08 - movement.x * 0.15,
                    0.025 + RANDOM.nextDouble() * 0.045,
                    offset.z * 0.045 + (RANDOM.nextDouble() - 0.5) * 0.08 - movement.z * 0.15);
            HEAD_VENT_PARTICLES.add(new HeadVentParticle(head.add(offset), motion, heat, true));
        }
    }

    private static boolean isLocalIgnitionArmed() {
        var mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return false;
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.getIgnitionCharge() >= data.getMaxIgnitionCharge()
                && data.getIgnitionCooldownTicks() <= 0;
    }

    private static void tickActiveCharges(BufferBuilder buffer, Matrix4f matrix, double gameTime) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;

        ACTIVE_CHARGES.entrySet().removeIf(entry -> {
            ActiveCharge charge = entry.getValue();
            Entity entity = mc.level.getEntity(charge.entityId);
            if (entity != null && entity.isAlive()) {
                charge.pos = entity.position().add(0, entity.getBbHeight() * 0.5, 0);
            } else {
                charge.startFade(gameTime);
            }

            if (gameTime > charge.timeoutGameTime) {
                charge.startFade(gameTime);
            }

            float visibility = charge.visibility(gameTime);
            if (visibility <= 0.0f) return true;

            if (RANDOM.nextInt(2) == 0) {
                spawnTrailParticles(charge.pos, entity);
            }
            drawPrimedMarks(buffer, matrix, charge.pos, gameTime - charge.startGameTime, visibility);
            return false;
        });
    }

    private static void tickParticles(BufferBuilder buffer, Matrix4f matrix) {
        PARTICLES.removeIf(p -> !p.tick());
        if (PARTICLES.isEmpty()) return;

        var mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Vector3f up = mc.gameRenderer.getMainCamera().getUpVector();
        Vector3f left = mc.gameRenderer.getMainCamera().getLeftVector();

        for (IgnitionParticle p : PARTICLES) {
            if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.25) continue;
            float life = 1.0f - ((float) p.age / p.maxAge);
            int alpha = (int) (life * 235);
            int[] color = particleColor(p.colorSet);
            drawQuad(buffer, matrix, (float) p.prevX, (float) p.prevY, (float) p.prevZ,
                    left, up, p.size * 0.8f, alpha / 2, color[0], color[1], color[2]);
            drawQuad(buffer, matrix, (float) p.x, (float) p.y, (float) p.z,
                    left, up, p.size, alpha, color[0], color[1], color[2]);
        }
    }

    private static void drawPrimedMarks(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, double age, float visibility) {
        float pulse = (float) ((age % 6.0) / 6.0);
        int alpha = (int) (visibility * 230);
        drawFlatRing(buffer, matrix, pos, 0.42f + pulse * 0.18f, 0.07f, alpha, 255, 72, 8);
        drawFlatRing(buffer, matrix, pos.add(0, 0.42, 0), 0.28f + (1.0f - pulse) * 0.12f, 0.06f, alpha, 255, 190, 40);
        drawBlockyCore(buffer, matrix, pos, 0.12f + pulse * 0.03f, alpha);
    }

    private static void spawnTrailParticles(Vec3 pos, Entity entity) {
        Vec3 visualVel = Vec3.ZERO;
        if (entity != null) {
            visualVel = new Vec3(entity.getX() - entity.xo, entity.getY() - entity.yo, entity.getZ() - entity.zo);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 offset = new Vec3(
                    (RANDOM.nextDouble() - 0.5) * 1.0,
                    (RANDOM.nextDouble() - 0.5) * 1.1,
                    (RANDOM.nextDouble() - 0.5) * 1.0
            );
            Vec3 motion = new Vec3(
                    -visualVel.x * 0.35 + (RANDOM.nextDouble() - 0.5) * 0.18,
                    (RANDOM.nextDouble() - 0.35) * 0.16,
                    -visualVel.z * 0.35 + (RANDOM.nextDouble() - 0.5) * 0.18
            );
            PARTICLES.add(new IgnitionParticle(pos.add(offset), motion, RANDOM.nextInt(4) == 0));
        }
    }

    private static void spawnParticleBurst(Vec3 pos, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double theta = RANDOM.nextDouble() * Math.PI * 2.0;
            double y = (RANDOM.nextDouble() - 0.35) * speed;
            double horizontal = speed * (0.35 + RANDOM.nextDouble() * 0.65);
            Vec3 motion = new Vec3(Math.cos(theta) * horizontal, y, Math.sin(theta) * horizontal);
            PARTICLES.add(new IgnitionParticle(pos, motion, RANDOM.nextBoolean()));
        }
    }

    private static void tickHeadVentParticles(BufferBuilder buffer, Matrix4f matrix) {
        renderHeadVentParticles(buffer, matrix, false);
    }

    public static void renderHeadSmoke(BufferBuilder buffer, Matrix4f matrix) {
        renderHeadVentParticles(buffer, matrix, true);
    }

    private static void renderHeadVentParticles(BufferBuilder buffer, Matrix4f matrix, boolean smokePass) {
        var mc = Minecraft.getInstance();
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        Vector3f up = mc.gameRenderer.getMainCamera().getUpVector();
        Vector3f left = mc.gameRenderer.getMainCamera().getLeftVector();
        float partialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);

        for (HeadVentParticle p : HEAD_VENT_PARTICLES) {
            if (p.smoke != smokePass) continue;
            if (firstPerson && camera.distanceToSqr(p.x, p.y, p.z) < 0.35) continue;
            float age = p.age + partialTick;
            float life = Math.max(0.0f, 1.0f - age / p.maxAge);
            float x = (float) (p.prevX + (p.x - p.prevX) * partialTick);
            float y = (float) (p.prevY + (p.y - p.prevY) * partialTick);
            float z = (float) (p.prevZ + (p.z - p.prevZ) * partialTick);
            if (p.smoke) {
                float growth = 0.8f + (1.0f - life) * 1.8f;
                int alpha = (int) (85 * life * Math.min(1.0f, age / 4.0f));
                int shade = 65 + p.colorVariant * 9;
                drawRectangle(buffer, matrix, x, y, z, left, up,
                        p.width * growth, p.height * growth, alpha, shade + 5, shade, shade - 3);
                continue;
            }
            float flicker = 0.75f + 0.25f * (float) Math.sin(p.phase + age * p.flutter * 2.0f);
            float stretch = 0.8f + 0.35f * (float) Math.sin(p.phase + age * p.flutter);
            float scale = (0.65f + 0.35f * life) * flicker;
            int alpha = (int) ((175 + p.heat * 70) * life * flicker);
            int[] color = headVentColor(p.heat, p.colorVariant);
            drawRectangle(buffer, matrix, x, y, z, left, up,
                    p.width * scale, p.height * scale * stretch, alpha, color[0], color[1], color[2]);
            if (p.colorVariant == 1 || (p.heat > 0.7f && p.colorVariant == 0)) {
                drawRectangle(buffer, matrix, x, y, z, left, up,
                        p.width * scale * 0.4f, p.height * scale * stretch * 0.55f,
                        alpha, 255, 246, 175);
            }
        }
    }
    private static void triggerCamera(Vec3 pos, float shake, float flash, float redFlash) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double distance = mc.player.position().distanceTo(pos);
        float proximity = (float) Math.max(0.0, 1.0 - distance / 48.0);
        if (distance < 8.0) {
            proximity = Math.max(proximity, 0.9f);
        }
        if (proximity <= 0.0f) return;

        screenShakeIntensity = Math.max(screenShakeIntensity, shake * proximity);
        WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, 0.24f * proximity);
        flashIntensity = Math.max(flashIntensity, flash * proximity);
        redFlashIntensity = Math.max(redFlashIntensity, redFlash * proximity);
    }

    private static int[] particleColor(int colorSet) {
        return switch (colorSet) {
            case 1 -> new int[]{255, 230, 84};
            case 2 -> new int[]{255, 104, 16};
            default -> new int[]{255, 44, 8};
        };
    }

    private static int[] headVentColor(float heat, int colorVariant) {
        return switch (colorVariant) {
            case 1 -> new int[]{255, 215 + (int) (heat * 30), 85};
            case 2 -> new int[]{255, 48 + (int) (heat * 40), 6};
            case 3 -> new int[]{235, 24, 8};
            default -> new int[]{255, 115 + (int) (heat * 45), 12};
        };
    }

    private static void drawRectangle(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                      Vector3f l, Vector3f u, float w, float h, int a, int r, int g, int bl) {
        b.addVertex(m, x - l.x() * w - u.x() * h, y - l.y() * w - u.y() * h, z - l.z() * w - u.z() * h).setColor(r, g, bl, a);
        b.addVertex(m, x + l.x() * w - u.x() * h, y + l.y() * w - u.y() * h, z + l.z() * w - u.z() * h).setColor(r, g, bl, a);
        b.addVertex(m, x + l.x() * w + u.x() * h, y + l.y() * w + u.y() * h, z + l.z() * w + u.z() * h).setColor(r, g, bl, a);
        b.addVertex(m, x - l.x() * w + u.x() * h, y - l.y() * w + u.y() * h, z - l.z() * w + u.z() * h).setColor(r, g, bl, a);
    }
    private static void drawFlatRing(BufferBuilder buffer, Matrix4f matrix, Vec3 center, float radius, float thickness,
                                     int alpha, int r, int g, int b) {
        int segments = 16;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            Vec3 outer0 = center.add(Math.cos(a0) * (radius + thickness), 0, Math.sin(a0) * (radius + thickness));
            Vec3 outer1 = center.add(Math.cos(a1) * (radius + thickness), 0, Math.sin(a1) * (radius + thickness));
            Vec3 inner1 = center.add(Math.cos(a1) * (radius - thickness), 0, Math.sin(a1) * (radius - thickness));
            Vec3 inner0 = center.add(Math.cos(a0) * (radius - thickness), 0, Math.sin(a0) * (radius - thickness));
            buffer.addVertex(matrix, (float) outer0.x, (float) outer0.y, (float) outer0.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float) outer1.x, (float) outer1.y, (float) outer1.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float) inner1.x, (float) inner1.y, (float) inner1.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, (float) inner0.x, (float) inner0.y, (float) inner0.z).setColor(r, g, b, alpha);
        }
    }

    private static void drawBlockyCore(BufferBuilder buffer, Matrix4f matrix, Vec3 pos, float size, int alpha) {
        var cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        drawQuad(buffer, matrix, (float) pos.x, (float) pos.y, (float) pos.z,
                cam.getLeftVector(), cam.getUpVector(), size * 2.0f, alpha, 255, 56, 8);
        drawQuad(buffer, matrix, (float) pos.x, (float) pos.y, (float) pos.z,
                cam.getLeftVector(), cam.getUpVector(), size, 255, 255, 220, 70);
    }

    private static void drawQuad(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                 Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + (-l.x() - u.x()) * s, y + (-l.y() - u.y()) * s, z + (-l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() - u.x()) * s, y + (l.y() - u.y()) * s, z + (l.z() - u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (l.x() + u.x()) * s, y + (l.y() + u.y()) * s, z + (l.z() + u.z()) * s).setColor(r, g, bl, a);
        b.addVertex(m, x + (-l.x() + u.x()) * s, y + (-l.y() + u.y()) * s, z + (-l.z() + u.z()) * s).setColor(r, g, bl, a);
    }

    private static void drawDiamond(BufferBuilder b, Matrix4f m, float x, float y, float z,
                                    Vector3f l, Vector3f u, float s, int a, int r, int g, int bl) {
        b.addVertex(m, x + -u.x() * s, y + -u.y() * s, z + -u.z() * s).setColor(r, g, bl, a);
        b.addVertex(m, x + l.x() * s, y + l.y() * s, z + l.z() * s).setColor(r, g, bl, a);
        b.addVertex(m, x + u.x() * s, y + u.y() * s, z + u.z() * s).setColor(r, g, bl, a);
        b.addVertex(m, x + -l.x() * s, y + -l.y() * s, z + -l.z() * s).setColor(r, g, bl, a);
    }
}
