package net.sonflowers.jeecore.gene.terragolem.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.sonflowers.jeecore.JeeCore;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/**
 * Maintains client overheating flashes, sparks, and smoke for the owner and tracking observers.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class TerragolemMalfunctionRenderer {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final List<Particle> PARTICLES = new ArrayList<>();
    private static ClientLevel level;
    private static boolean warningWasBright;
    private static boolean warningActive;
    private static long lastWarningTick = Long.MIN_VALUE;
    private static class State {
        float heat;
        long timeoutEnd;
    }
    private static class Particle {
        Vec3 position, previous, velocity;
        Vec3 vortexCenter;
        double vortexRise = 0.065;
        final boolean smoke;
        final int lifetime;
        final float width, height, phase, frequency, intensity, speedVariation;
        int age;
        Particle(Vec3 position, Vec3 velocity, boolean smoke, float intensity) {
            this(position, velocity, smoke, intensity, false);
        }
        Particle(Vec3 position, Vec3 velocity, boolean smoke, float intensity, boolean brief) {
            this.position = this.previous = position;
            this.smoke = smoke;
            speedVariation = 0.82f + RANDOM.nextFloat() * 0.36f;
            this.velocity = velocity.scale(smoke ? 1 : speedVariation);
            this.intensity = intensity;
            lifetime = smoke ? 35 + RANDOM.nextInt(20) : brief ? 17 + RANDOM.nextInt(3) : 8 + RANDOM.nextInt(8);
            if (smoke) {
                width = 0.16f + RANDOM.nextFloat() * 0.22f;
                height = 0.16f + RANDOM.nextFloat() * 0.25f;
            } else {
                // Correlated dimensions keep sparks compact, with a mix of broad and slightly tall
                // shapes.
                width = 0.018f + RANDOM.nextFloat() * 0.025f;
                height = width * (0.60f + RANDOM.nextFloat() * 0.75f);
            }
            phase = RANDOM.nextFloat() * (float) Math.PI * 2;
            frequency = smoke ? 0.12f + RANDOM.nextFloat() * 0.1f : 0.40f + RANDOM.nextFloat() * 0.35f;
        }
        boolean tick() {
            previous = position;
            if (vortexCenter != null) {
                Vec3 offset = position.subtract(vortexCenter);
                double angle = (0.16 + Math.sin(phase + age * frequency) * 0.035) * speedVariation;
                double cos = Math.cos(angle), sin = Math.sin(angle);
                double radiusDrift = 0.997 + Math.sin(phase + age * frequency * 0.8) * 0.004;
                position = vortexCenter.add((offset.x * cos - offset.z * sin) * radiusDrift,
                        offset.y + vortexRise * speedVariation, (offset.x * sin + offset.z * cos) * radiusDrift);
                return ++age < lifetime;
            }
            position = position.add(velocity);
            double turbulence = smoke ? 0.002 : 0.01;
            velocity = velocity.scale(smoke ? 0.96 : 0.94).add(
                    Math.sin(phase + age * frequency) * turbulence,
                    smoke ? 0.0015 : 0.0006 * speedVariation,
                    Math.cos(phase + age * frequency * 0.8) * turbulence);
            return ++age < lifetime;
        }
    }

    private static void ensureLevel() {
        ClientLevel current = Minecraft.getInstance().level;
        if (current != level) {
            STATES.clear(); PARTICLES.clear(); level = current;
            warningWasBright = false;
            warningActive = false;
            lastWarningTick = Long.MIN_VALUE;
        }
    }

    public static void update(UUID player, float heat, int timeoutTicks) {
        ensureLevel();
        if (level == null) return;
        State state = STATES.computeIfAbsent(player, ignored -> new State());
        if (heat >= 0) state.heat = Math.max(0, Math.min(1, heat));
        if (timeoutTicks > 0) state.timeoutEnd = level.getGameTime() + timeoutTicks;
    }

    public static void burst(UUID id, boolean smoke) {
        ensureLevel();
        if (level == null) return;
        Player player = level.getPlayerByUUID(id);
        if (player == null) return;
        double startAngle = RANDOM.nextDouble() * Math.PI * 2;
        for (int i = 0; i < 12 && PARTICLES.size() < 512; i++) {
            double angle = startAngle + i * 2.399963 + (RANDOM.nextDouble() - 0.5) * 0.15;
            Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
            Vec3 pos = player.position().add(radial.scale(0.45 + RANDOM.nextDouble() * 0.12))
                    .add(0, 0.08 + (i + RANDOM.nextDouble()) / 12.0 * player.getBbHeight() * 0.9, 0);
            Vec3 motion = radial.scale(0.045 + RANDOM.nextDouble() * 0.07)
                    .add(0, (RANDOM.nextDouble() - 0.5) * 0.13, 0);
            Particle spark = new Particle(pos, motion, false, 0.7f, true);
            spark.vortexCenter = player.position();
            spark.vortexRise = 0.012 + RANDOM.nextDouble() * 0.008;
            PARTICLES.add(spark);
        }
        if (smoke) {
            for (int i = 0; i < 4 && PARTICLES.size() < 512; i++) {
                Vec3 pos = player.position().add((RANDOM.nextDouble() - 0.5) * 1.2,
                        0.5 + RANDOM.nextDouble() * player.getBbHeight(), (RANDOM.nextDouble() - 0.5) * 1.2);
                PARTICLES.add(new Particle(pos, new Vec3((RANDOM.nextDouble() - 0.5) * 0.04,
                        0.035, (RANDOM.nextDouble() - 0.5) * 0.04), true, 0.7f));
            }
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ensureLevel();
        if (level == null || Minecraft.getInstance().isPaused()) return;
        PARTICLES.removeIf(p -> !p.tick());
        long now = level.getGameTime();
        tickWarning(now);
        // Full effect synchronization also supports observers who arrive midway through
        // overheating.
        for (Player player : level.players()) {
            if (player.hasEffect(ModMobEffects.OVERHEATED)) STATES.computeIfAbsent(player.getUUID(), ignored -> new State());
        }
        STATES.entrySet().removeIf(entry -> {
            Player player = level.getPlayerByUUID(entry.getKey());
            State state = entry.getValue();
            if (player == null || !player.isAlive()) return true;
            boolean timeout = now < state.timeoutEnd;
            boolean overheated = player.hasEffect(ModMobEffects.OVERHEATED);
            float heat = overheated ? Math.max(0.75f, state.heat) : state.heat;
            if (heat <= 0 && !timeout) return true;
            Vec3 forward = player.getLookAngle();
            Vec3 face = player.getEyePosition().add(forward.scale(0.3));
            if (now % 3 == 0 && (timeout || RANDOM.nextFloat() < heat)) {
                for (int i = 0; i < 3; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2;
                    Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Vec3 pos = player.position().add(radial.scale(0.25 + RANDOM.nextDouble() * 0.2))
                            .add(0, 0.15 + RANDOM.nextDouble() * player.getBbHeight(), 0);
                    double vertical = 0.25 + RANDOM.nextDouble() * 0.7;
                    double horizontal = Math.sqrt(1 - vertical * vertical);
                    double speed = 0.045 + RANDOM.nextDouble() * 0.07;
                    Vec3 motion = new Vec3(radial.x * horizontal, vertical, radial.z * horizontal).scale(speed);
                    if (PARTICLES.size() < 512) PARTICLES.add(new Particle(pos, motion, false, 1));
                }
            }
            if (timeout && now % 16 == 0) {
                for (int i = 0; i < 1; i++) {
                    level.addParticle(ParticleTypes.ANGRY_VILLAGER,
                            face.x + (RANDOM.nextDouble() - 0.5) * 0.6,
                            face.y + 0.2 + RANDOM.nextDouble() * 0.25,
                            face.z + (RANDOM.nextDouble() - 0.5) * 0.6, 0, 0, 0);
                }
            }
            if ((timeout || overheated || heat >= 0.75f) && now % 6 == 0) {
                double angle = RANDOM.nextDouble() * Math.PI * 2;
                Vec3 radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 pos = player.position().add(radial.scale(0.28 + RANDOM.nextDouble() * 0.2))
                        .add(0, 0.2 + RANDOM.nextDouble() * player.getBbHeight() * 0.55, 0);
                level.addParticle(ParticleTypes.FLAME, pos.x, pos.y, pos.z,
                        radial.x * 0.012, 0.015 + RANDOM.nextDouble() * 0.03, radial.z * 0.012);
            }
            if ((timeout || heat > 0) && now % 3 == 0 && (timeout || RANDOM.nextFloat() < heat)) {
                Vec3 pos = player.position().add((RANDOM.nextDouble() - 0.5) * 1.4,
                        0.5 + RANDOM.nextDouble() * player.getBbHeight(), (RANDOM.nextDouble() - 0.5) * 1.4);
                Vec3 motion = new Vec3((RANDOM.nextDouble() - 0.5) * 0.04,
                        0.015 + RANDOM.nextDouble() * 0.025, (RANDOM.nextDouble() - 0.5) * 0.04);
                if (PARTICLES.size() < 512) PARTICLES.add(new Particle(pos, motion, true, timeout ? 1 : heat));
            }
            return false;
        });
    }

    private static void tickWarning(long now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive() || GeneHelper.getPlayerGene(mc.player) != Gene.TERRAGOLEM) {
            warningWasBright = false;
            warningActive = false;
            return;
        }
        var data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        float heat = data.getMaxOverheatBar() > 0 ? data.getOverheatBar() / (float) data.getMaxOverheatBar() : 0;
        boolean overheated = data.isOverheatPrimaryLocked();
        boolean enteringWarning = (heat >= 0.75f || overheated) && !warningActive;
        warningActive = heat >= 0.75f || overheated;
        boolean bright = warningActive
                && TerragolemOverheatBarRenderer.warningFlash(now, heat, overheated) >= 0.75f;
        if ((enteringWarning || (bright && !warningWasBright)) && (lastWarningTick == Long.MIN_VALUE || now - lastWarningTick >= 4)) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(),
                    overheated ? 1.1f : 1.1f + Math.max(0, heat - 0.75f) * 2, 0.55f));
            lastWarningTick = now;
        }
        warningWasBright = bright;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PARTICLES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        pose.pushPose(); pose.translate(-camera.x, -camera.y, -camera.z);
        RenderSystem.enableBlend(); RenderSystem.enableDepthTest(); RenderSystem.disableCull();
        RenderSystem.depthMask(false); RenderSystem.setShader(GameRenderer::getPositionColorShader);
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vector3f left = event.getCamera().getLeftVector(), up = event.getCamera().getUpVector();
        PARTICLES.sort(Comparator.comparingDouble((Particle p) -> camera.distanceToSqr(p.position)).reversed());
        for (boolean smoke : new boolean[]{true, false}) {
            if (smoke) RenderSystem.defaultBlendFunc();
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (Particle p : PARTICLES) {
                if (p.smoke != smoke) continue;
                Vec3 pos = p.previous.lerp(p.position, partial);
                if (mc.options.getCameraType().isFirstPerson() && camera.distanceToSqr(pos) < 0.3) continue;
                float age = p.age + partial;
                float life = Math.max(0, 1 - age / p.lifetime);
                float phase = p.phase + age * p.frequency;
                float flutter = smoke ? 0.9f + 0.1f * Mth.sin(phase)
                        : 0.85f + 0.14f * Mth.sin(phase) + 0.10f * Mth.sin(phase * 2.37f + 1.1f);
                float size = smoke ? (0.8f + (1 - life) * 1.3f) * flutter : flutter;
                int alpha = smoke ? (int) (155 * life * p.intensity * Math.min(1, age / 5)) : (int) (230 * life * flutter * p.intensity);
                float width = p.width * size;
                float height = p.height * size * (0.9f + 0.1f * Mth.sin(phase * 0.7f));
                if (smoke) renderLayeredSmoke(buffer, pose.last().pose(), pos, left, up, width, height, alpha, phase);
                else renderLayeredFlame(buffer, pose.last().pose(), pos, left, up, width, height, alpha, phase);
            }
            MeshData mesh = buffer.build();
            if (mesh != null) BufferUploader.drawWithShader(mesh);
        }
        RenderSystem.defaultBlendFunc(); RenderSystem.depthMask(true); RenderSystem.enableCull();
        pose.popPose();
    }

    private static void renderLayeredFlame(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                                          Vector3f left, Vector3f up, float width, float height, int alpha, float phase) {
        // Additive nested rectangles: diffuse orange glow, flame body, yellow heat, white-hot core.
        float pulse = 0.9f + 0.14f * Mth.sin(phase * 1.6f);
        rectangle(buffer, matrix, pos, left, up, width * 2.5f, height * 2.1f, (int) (alpha * 0.12f), 255, 72, 8);
        rectangle(buffer, matrix, pos, left, up, width * 1.55f, height * 1.45f, (int) (alpha * 0.32f), 255, 118, 16);
        rectangle(buffer, matrix, pos, left, up, width, height, (int) (alpha * 0.85f), 255, 165, 35);
        Vec3 core = pos.add(left.x() * width * Mth.sin(phase) * 0.18,
                left.y() * width * Mth.sin(phase) * 0.18, left.z() * width * Mth.sin(phase) * 0.18);
        rectangle(buffer, matrix, core, left, up, width * 0.62f * pulse, height * 0.65f,
                (int) (alpha * 0.95f), 255, 228, 132);
        rectangle(buffer, matrix, core, left, up, width * 0.35f * pulse, height * 0.36f * pulse,
                alpha, 255, 252, 232);
    }

    private static void renderLayeredSmoke(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                                          Vector3f left, Vector3f up, float width, float height, int alpha, float phase) {
        // Standard alpha blending keeps the overlapping charcoal lobes dense rather than luminous.
        rectangle(buffer, matrix, pos, left, up, width * 1.12f, height * 1.08f, (int) (alpha * 0.25f), 58, 53, 47);
        rectangle(buffer, matrix, pos, left, up, width, height, (int) (alpha * 0.72f), 32, 29, 26);
        float drift = Mth.sin(phase * 0.6f) * width * 0.18f;
        Vec3 lobe = pos.add(left.x() * (width * 0.22 + drift) + up.x() * height * 0.12,
                left.y() * (width * 0.22 + drift) + up.y() * height * 0.12,
                left.z() * (width * 0.22 + drift) + up.z() * height * 0.12);
        rectangle(buffer, matrix, lobe, left, up, width * 0.68f, height * 0.76f, (int) (alpha * 0.50f), 20, 19, 18);
        rectangle(buffer, matrix, pos, left, up, width * 0.48f, height * 0.60f, (int) (alpha * 0.18f), 78, 66, 52);
    }

    private static void rectangle(BufferBuilder b, Matrix4f m, Vec3 pos, Vector3f l, Vector3f u,
                                  float w, float h, int a, int r, int g, int blue) {
        a = Mth.clamp(a, 0, 255);
        if (a == 0) return;
        float x = (float) pos.x, y = (float) pos.y, z = (float) pos.z;
        b.addVertex(m, x - l.x()*w - u.x()*h, y - l.y()*w - u.y()*h, z - l.z()*w - u.z()*h).setColor(r,g,blue,a);
        b.addVertex(m, x + l.x()*w - u.x()*h, y + l.y()*w - u.y()*h, z + l.z()*w - u.z()*h).setColor(r,g,blue,a);
        b.addVertex(m, x + l.x()*w + u.x()*h, y + l.y()*w + u.y()*h, z + l.z()*w + u.z()*h).setColor(r,g,blue,a);
        b.addVertex(m, x - l.x()*w + u.x()*h, y - l.y()*w + u.y()*h, z - l.z()*w + u.z()*h).setColor(r,g,blue,a);
    }
}
