package net.sonflowers.jeecore.core.client;

import net.sonflowers.jeecore.gene.dryad.client.DryadElixirTrailRenderer;
import net.sonflowers.jeecore.gene.dryad.client.HealRenderer;
import net.sonflowers.jeecore.gene.dryad.client.RootSnareRenderer;
import net.sonflowers.jeecore.gene.infernal.client.InfernalAerialChargeRenderer;
import net.sonflowers.jeecore.gene.infernal.client.InfernalChargeVfxRenderer;
import net.sonflowers.jeecore.gene.infernal.client.RageRenderer;
import net.sonflowers.jeecore.gene.infernal.client.SmokeExitRenderer;
import net.sonflowers.jeecore.gene.infernal.client.SmokeRenderer;
import net.sonflowers.jeecore.gene.nimbus.client.CloudRenderer;
import net.sonflowers.jeecore.gene.nimbus.client.WindRenderer;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemBurstRenderer;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.core.server.BeamManager;
import net.sonflowers.jeecore.gene.nimbus.server.CloudManager;
import net.sonflowers.jeecore.gene.dryad.server.HealManager;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Coordinates client beam rendering, camera feedback, screen overlays, and shared ability effect
 * passes.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class BeamRenderer {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final ResourceLocation NAUSEA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");
    private static final List<Player> BEAMING_PLAYERS = new ArrayList<>();
    private static final List<Player> RAGING_PLAYERS = new ArrayList<>();
    private static final List<Player> SMOKING_PLAYERS = new ArrayList<>();
    private static final List<Player> CLOUDED_PLAYERS = new ArrayList<>();
    private static final List<Player> POUNCING_PLAYERS = new ArrayList<>();
    private static final List<Player> READY_UP_PLAYERS = new ArrayList<>();

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float gustShake = net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient.chargeShake();
        if (gustShake > 0) {
            float power = gustShake * gustShake;
            event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5f) * power * 25);
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5f) * power * 25);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5f) * power * 30);
        }
        if (WindRenderer.screenShakeIntensity > 0) {
            float s = WindRenderer.screenShakeIntensity;
            float power = s * s;
            event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5f) * power * 25.0f);
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5f) * power * 25.0f);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5f) * power * 35.0f);
            if (InfernalAerialChargeRenderer.shouldUseHeavyShakeDecay()) {
                WindRenderer.screenShakeIntensity *= 0.975f;
                WindRenderer.screenShakeIntensity -= 0.0015f;
                InfernalAerialChargeRenderer.tickHeavyShakeDecay();
            } else {
                WindRenderer.screenShakeIntensity *= 0.94f;
                WindRenderer.screenShakeIntensity -= 0.004f;
            }
            if (WindRenderer.screenShakeIntensity < 0) WindRenderer.screenShakeIntensity = 0;
        }

        if (InfernalChargeVfxRenderer.screenShakeIntensity > 0) {
            float s = InfernalChargeVfxRenderer.screenShakeIntensity;
            float power = s * s;
            event.setPitch(event.getPitch() + (RANDOM.nextFloat() - 0.5f) * power * 22.0f);
            event.setYaw(event.getYaw() + (RANDOM.nextFloat() - 0.5f) * power * 28.0f);
            event.setRoll(event.getRoll() + (RANDOM.nextFloat() - 0.5f) * power * 22.0f);
            InfernalChargeVfxRenderer.screenShakeIntensity *= 0.86f;
            InfernalChargeVfxRenderer.screenShakeIntensity -= 0.01f;
            if (InfernalChargeVfxRenderer.screenShakeIntensity < 0) InfernalChargeVfxRenderer.screenShakeIntensity = 0;
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        event.setFOV(event.getFOV() * InfernalAerialChargeRenderer.getFovMultiplier() * TerragolemReadyUpRenderer.getFovMultiplier()
                * net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient.getFovMultiplier(event.getPartialTick()));
    }

    public static float rageFlashIntensity = 0;

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        // Rage mode nausea overlay for local player with pulsating intensity.
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && RageManager.isRaging(mc.player.getUUID())) {
            PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
            int rageBar = data.getRageBar();
            int maxRage = data.getMaxRageBar();
            double gameTime = mc.level != null ? mc.level.getGameTime() : 0.0;

            // Base intensity from rage bar percentage.
            float ragePercent = maxRage > 0 ? (float) rageBar / maxRage : 0.0f;
            float visualPercent = getRageEffectPercent(ragePercent);
            float baseIntensity = Math.min(0.46f, 0.08f + visualPercent * 0.38f);
            // Pulsating effect using sine wave.
            float pulse = (float) Math.sin(gameTime * 0.18) * 0.14f + 0.9f;
            float intensity = baseIntensity * pulse;

            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            // Use an orange tint for rage feedback.
            RenderSystem.setShaderColor(1.0f, 0.22f, 0.025f, intensity);
            event.getGuiGraphics().blit(NAUSEA_TEXTURE, 0, 0, 0, 0, w, h, w, h);
            int redWashAlpha = (int) (Math.min(0.2f, intensity * 0.24f) * 255);
            int redWash = (redWashAlpha << 24) | (180 << 16) | (18 << 8) | 4;
            event.getGuiGraphics().fill(0, 0, w, h, redWash);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        // Terragolem ready-up/hold: pulsing hot orange overlay, synced with the head flare.
        if (mc.player != null && mc.level != null && TerragolemReadyUpRenderer.isFlareVisible(mc.player.getUUID())) {
            float charge = TerragolemReadyUpRenderer.getRenderCharge(mc.player.getUUID());
            double tgGameTime = mc.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(true);
            float pulse = TerragolemReadyUpRenderer.getPulse(tgGameTime);
            float intensity = Math.min(0.55f, (0.10f + 0.30f * charge) * (0.7f + 0.5f * pulse));
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 0.42f, 0.03f, intensity);
            event.getGuiGraphics().blit(NAUSEA_TEXTURE, 0, 0, 0, 0, w, h, w, h);
            int orangeWashAlpha = (int) (Math.min(0.3f, intensity * 0.35f) * 255);
            int orangeWash = (orangeWashAlpha << 24) | (255 << 16) | (110 << 8) | 15;
            event.getGuiGraphics().fill(0, 0, w, h, orangeWash);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        if (TerragolemReadyUpRenderer.whiteFlashIntensity > 0) {
            float alpha = Math.min(0.95f, TerragolemReadyUpRenderer.whiteFlashIntensity * 0.9f);
            int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            TerragolemReadyUpRenderer.whiteFlashIntensity *= 0.86f;
            if (TerragolemReadyUpRenderer.whiteFlashIntensity < 0.01f) TerragolemReadyUpRenderer.whiteFlashIntensity = 0;
        }
        if (TerragolemReadyUpRenderer.bigFlashIntensity > 0) {
            // Timeout big stun: longer, slower-decaying white flash.
            float alpha = Math.min(0.95f, TerragolemReadyUpRenderer.bigFlashIntensity * 0.9f);
            int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            TerragolemReadyUpRenderer.bigFlashIntensity *= 0.955f;
            if (TerragolemReadyUpRenderer.bigFlashIntensity < 0.01f) TerragolemReadyUpRenderer.bigFlashIntensity = 0;
        }
        if (TerragolemReadyUpRenderer.overheatFlashIntensity > 0) {
            // Overheat debuff: way longer, very slowly decaying pale red flash.
            float alpha = Math.min(0.5f, TerragolemReadyUpRenderer.overheatFlashIntensity * 0.55f);
            int color = ((int) (alpha * 255) << 24) | (255 << 16);
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            TerragolemReadyUpRenderer.overheatFlashIntensity *= 0.985f;
            if (TerragolemReadyUpRenderer.overheatFlashIntensity < 0.01f) TerragolemReadyUpRenderer.overheatFlashIntensity = 0;
        }

        if (rageFlashIntensity > 0) {
            float alpha = Math.min(0.35f, rageFlashIntensity * 0.5f);
            int color = ((int) (alpha * 255) << 24) | (255 << 16) | (255 << 8) | 255;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            rageFlashIntensity *= 0.9f;
            if (rageFlashIntensity < 0.01f) rageFlashIntensity = 0;
        }
        if (InfernalChargeVfxRenderer.redFlashIntensity > 0) {
            float alpha = Math.min(0.94f, InfernalChargeVfxRenderer.redFlashIntensity * 0.82f);
            int color = ((int) (alpha * 255) << 24) | (255 << 16) | (150 << 8) | 142;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            InfernalChargeVfxRenderer.redFlashIntensity *= 0.48f;
            if (InfernalChargeVfxRenderer.redFlashIntensity < 0.01f) InfernalChargeVfxRenderer.redFlashIntensity = 0;
        }
        if (InfernalChargeVfxRenderer.whiteFlashIntensity > 0) {
            float alpha = Math.min(0.95f, InfernalChargeVfxRenderer.whiteFlashIntensity * 0.9f);
            int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            InfernalChargeVfxRenderer.whiteFlashIntensity *= 0.86f;
            if (InfernalChargeVfxRenderer.whiteFlashIntensity < 0.01f) InfernalChargeVfxRenderer.whiteFlashIntensity = 0;
        }
        if (InfernalChargeVfxRenderer.flashIntensity > 0) {
            float alpha = Math.min(0.5f, InfernalChargeVfxRenderer.flashIntensity * 0.42f);
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 0.32f, 0.02f, alpha);
            event.getGuiGraphics().blit(NAUSEA_TEXTURE, 0, 0, 0, 0, w, h, w, h);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            InfernalChargeVfxRenderer.flashIntensity *= 0.84f;
            if (InfernalChargeVfxRenderer.flashIntensity < 0.01f) InfernalChargeVfxRenderer.flashIntensity = 0;
        }
        if (InfernalAerialChargeRenderer.getImpactFrameFlash() > 0) {
            float flash = InfernalAerialChargeRenderer.getImpactFrameFlash();
            float alpha = Math.min(0.82f, flash * 0.7f);
            int color = ((int) (alpha * 255) << 24) | (255 << 16) | (52 << 8) | 8;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            event.getGuiGraphics().fill(0, 0, w, h, color);
            InfernalAerialChargeRenderer.decayImpactFrameFlash();
        }

        float sporeFade = net.sonflowers.jeecore.gene.dryad.client.SporeRingRenderer.localOverlayFade(
                event.getPartialTick().getGameTimeDeltaPartialTick(true));
        boolean sporeGuard = sporeFade > 0 || (mc.player != null && mc.player.hasEffect(
                net.sonflowers.jeecore.core.registry.ModMobEffects.SPORE_GUARD));
        if (mc.player != null && mc.level != null && (sporeGuard || isLocalPlayerInHealZone(mc))) {
            float pulse = (float) Math.sin(mc.level.getGameTime() * 0.18) * 0.06f + 0.88f;
            float alpha = 0.22f * pulse;
            if (sporeGuard) alpha *= sporeFade;
            int w = mc.getWindow().getGuiScaledWidth();
            int h = mc.getWindow().getGuiScaledHeight();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(sporeGuard ? 0.60f : 1.0f, sporeGuard ? 1.0f : 0.96f,
                    sporeGuard ? 0.72f : 0.42f, alpha);
            event.getGuiGraphics().blit(NAUSEA_TEXTURE, 0, 0, 0, 0, w, h, w, h);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        var mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer == null) return;
        var level = mc.level;
        if (level == null) return;

        WindRenderer.prune();

        boolean localPlayerIsBeaming = BeamManager.isBeaming(localPlayer);
        if (localPlayerIsBeaming) {
            WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, 0.18f);
            localPlayer.setXRot(localPlayer.getXRot() + (RANDOM.nextFloat() - 0.5f) * 0.14f);
            localPlayer.setYRot(localPlayer.getYRot() + (RANDOM.nextFloat() - 0.5f) * 0.14f);
        }

        BEAMING_PLAYERS.clear();
        RAGING_PLAYERS.clear();
        SMOKING_PLAYERS.clear();
        CLOUDED_PLAYERS.clear();
        POUNCING_PLAYERS.clear();
        READY_UP_PLAYERS.clear();

        boolean needsPlayerScan = BeamManager.hasBeamingPlayers()
                || RageManager.hasRagingPlayers()
                || SmokeManager.hasSmokingPlayers()
                || CloudManager.hasCloudedPlayers()
                || InfernalAerialChargeRenderer.isActive()
                || TerragolemReadyUpRenderer.hasVisibleFlares();
        List<Player> nearbyPlayers = needsPlayerScan
                ? level.getEntitiesOfClass(Player.class, localPlayer.getBoundingBox().inflate(130.0))
                : List.of();

        for (Player nearbyPlayer : nearbyPlayers) {
            if (BeamManager.isBeaming(nearbyPlayer)) {
                BEAMING_PLAYERS.add(nearbyPlayer);
            }
            boolean pouncing = InfernalAerialChargeRenderer.isPouncing(nearbyPlayer.getUUID());
            if (RageManager.isRaging(nearbyPlayer.getUUID()) && !pouncing) {
                RAGING_PLAYERS.add(nearbyPlayer);
            }
            if (SmokeManager.isSmoking(nearbyPlayer.getUUID())) {
                SMOKING_PLAYERS.add(nearbyPlayer);
            }
            if (CloudManager.isClouded(nearbyPlayer.getUUID())) {
                CLOUDED_PLAYERS.add(nearbyPlayer);
            }

            if (pouncing) {
                POUNCING_PLAYERS.add(nearbyPlayer);
            }
            if (TerragolemReadyUpRenderer.isFlareVisible(nearbyPlayer.getUUID())) {
                READY_UP_PLAYERS.add(nearbyPlayer);
            }
        }

        boolean anyoneIsBeaming = !BEAMING_PLAYERS.isEmpty();
        boolean anyoneIsRaging = !RAGING_PLAYERS.isEmpty();
        boolean anyoneIsSmoking = !SMOKING_PLAYERS.isEmpty();
        boolean anyoneIsClouded = !CLOUDED_PLAYERS.isEmpty();
        boolean anyoneIsPouncing = !POUNCING_PLAYERS.isEmpty();
        boolean anyoneIsReadyUp = !READY_UP_PLAYERS.isEmpty();
        boolean hasZones = !HealManager.getZones().isEmpty();
        boolean hasEndingHealZones = HealRenderer.hasEndingAuras();
        boolean hasWind = !WindRenderer.BURSTS.isEmpty()
                || net.sonflowers.jeecore.gene.nimbus.client.NimbusGustChargeRenderer.hasEffects();
        boolean hasDryadElixirs = DryadElixirTrailRenderer.hasVisibleElixirs(level, localPlayer.getBoundingBox().inflate(130.0));
        boolean hasExistingParticles = SmokeRenderer.hasParticles() || CloudRenderer.hasParticles()
                || InfernalChargeVfxRenderer.hasBursts() || SmokeExitRenderer.hasParticles()
                || DryadElixirTrailRenderer.hasParticles() || TerragolemBurstRenderer.hasBursts()
                || InfernalAerialChargeRenderer.isActive() || InfernalAerialChargeRenderer.hasImpactEffects()
                || RootSnareRenderer.hasEffects();

        if (!anyoneIsBeaming && !hasZones && !hasEndingHealZones && !hasWind && !anyoneIsRaging
                && !anyoneIsSmoking && !anyoneIsClouded && !anyoneIsPouncing
                && !hasDryadElixirs && !hasExistingParticles && !anyoneIsReadyUp) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        double gameTime = level.getGameTime() + partialTick;
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);

        renderHealDecalPass(matrix, camera, gameTime, HealRenderer.getInnerRingTexture(), true);
        renderHealDecalPass(matrix, camera, gameTime, HealRenderer.getOuterRingTexture(), false);

        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (Player entityPlayer : BEAMING_PLAYERS) {
            int holdColor = TerragolemReadyUpRenderer.getHoldBeamColor(entityPlayer.getUUID());
            if (holdColor >= 0) {
                // Terragolem right-button press hold: color ramps with hold time, range from
                // charged segments.
                renderHoldBeam(entityPlayer, buffer, matrix, partialTick, gameTime, holdColor,
                        TerragolemReadyUpRenderer.getHoldBeamRange(entityPlayer.getUUID()));
            } else {
                renderBeam(entityPlayer, buffer, matrix, partialTick, gameTime);
            }
        }
        for (Player entityPlayer : RAGING_PLAYERS) {
            RageRenderer.spawnParticles(entityPlayer);
        }
        for (Player entityPlayer : SMOKING_PLAYERS) {
            SmokeRenderer.spawnParticles(entityPlayer);
        }
        for (Player entityPlayer : POUNCING_PLAYERS) {
            SmokeRenderer.spawnPounceParticles(entityPlayer);
        }
        for (Player entityPlayer : READY_UP_PLAYERS) {
            // First-person culling: don't draw the cube around the local player's own head.
            if (entityPlayer == localPlayer && mc.options.getCameraType().isFirstPerson()) continue;
            TerragolemReadyUpRenderer.render(entityPlayer, buffer, matrix, partialTick, gameTime);
        }
        for (Player entityPlayer : CLOUDED_PLAYERS) {
            CloudRenderer.spawnParticles(entityPlayer);
        }

        for (var entry : HealManager.getZones().entrySet()) {
            Vec3 visualPos = new Vec3(entry.getKey().getX() + 0.5, entry.getKey().getY(), entry.getKey().getZ() + 0.5);
            if (visualPos.closerThan(camera, 130.0)) {
                HealRenderer.renderHealAura(visualPos, entry.getValue(), buffer, matrix, camera, gameTime);
            }
        }

        HealRenderer.renderEndingAuras(buffer, matrix, camera, gameTime);

        for (WindRenderer.ActiveBurst burst : WindRenderer.BURSTS) {
            if (burst.pos.closerThan(camera, 130.0)) {
                WindRenderer.render(burst, buffer, matrix, gameTime);
            }
        }

        net.sonflowers.jeecore.gene.nimbus.client.NimbusGustChargeRenderer.render(buffer, matrix, camera, gameTime, partialTick);
        DryadElixirTrailRenderer.render(level, camera, buffer, matrix, gameTime);
        InfernalChargeVfxRenderer.render(buffer, matrix, gameTime);
        TerragolemBurstRenderer.render(buffer, matrix, gameTime);

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }

        // Smoke exit owns separate tessellator buffers and changes blending. Finish the shared beam
        // mesh before starting those passes.
        SmokeExitRenderer.render(matrix, gameTime);
        RageRenderer.render(poseStack, camera, partialTick);
        SmokeRenderer.render(poseStack, camera, partialTick);
        InfernalAerialChargeRenderer.render(poseStack, camera, partialTick);
        RootSnareRenderer.render(poseStack, camera, partialTick);
        CloudRenderer.render(poseStack, camera, partialTick);

        // Terragolem impact/muzzle smoke needs standard alpha blending; rendering it in the
        // additive beam pass would make the dark quads effectively invisible.
        if (TerragolemBurstRenderer.hasBursts() || InfernalChargeVfxRenderer.hasBursts()) {
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferBuilder smokeBuffer = tesselator.begin(
                    VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            TerragolemBurstRenderer.renderSmoke(smokeBuffer, matrix, camera, gameTime);
            InfernalChargeVfxRenderer.renderHeadSmoke(smokeBuffer, matrix);
            MeshData smokeMesh = smokeBuffer.build();
            if (smokeMesh != null) {
                BufferUploader.drawWithShader(smokeMesh);
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        poseStack.popPose();
    }

    private static void renderHealDecalPass(Matrix4f matrix, Vec3 camera, double gameTime, ResourceLocation texture, boolean innerTexture) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        for (var entry : HealManager.getZones().entrySet()) {
            Vec3 visualPos = new Vec3(entry.getKey().getX() + 0.5, entry.getKey().getY(), entry.getKey().getZ() + 0.5);
            if (visualPos.closerThan(camera, 130.0)) {
                HealRenderer.renderHealAuraDecals(visualPos, entry.getValue(), buffer, matrix, gameTime, innerTexture);
            }
        }

        HealRenderer.renderEndingAuraDecals(buffer, matrix, camera, gameTime, innerTexture);

        MeshData meshData = buffer.build();
        if (meshData != null) {
            BufferUploader.drawWithShader(meshData);
        }
    }

    private static boolean isLocalPlayerInHealZone(Minecraft mc) {
        for (var pos : HealManager.getZones().keySet()) {
            double dx = mc.player.getX() - (pos.getX() + 0.5);
            double dz = mc.player.getZ() - (pos.getZ() + 0.5);
            if (dx * dx + dz * dz <= 12.25 && Math.abs(mc.player.getY() - (pos.getY() + 0.5)) <= 2.5) {
                return true;
            }
        }
        return false;
    }

    private static float getRageEffectPercent(float percent) {
        percent = Math.max(0.0f, Math.min(1.0f, percent));
        if (percent >= 0.2f) {
            float highPercent = (percent - 0.2f) / 0.8f;
            return 0.2f + 0.8f * (float) Math.pow(highPercent, 0.35);
        }

        float lowPercent = percent / 0.2f;
        return 0.2f * (float) Math.pow(lowPercent, 2.2);
    }

    private static void renderBeam(net.minecraft.world.entity.player.Player player, BufferBuilder buffer,
                                   Matrix4f matrix, float partialTick, double gameTime) {
        Vec3 start = player.getEyePosition(partialTick).add(0, -0.05, 0);
        double range = 30.0;
        Vec3 lookAngle = player.getViewVector(partialTick);
        Vec3 maxEndPos = start.add(lookAngle.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                start, maxEndPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        double distanceToTarget = blockHit.getLocation().distanceTo(start);

        AABB searchBox = player.getBoundingBox().expandTowards(lookAngle.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, start, maxEndPos, searchBox,
                (target) -> !target.isSpectator() && target.isPickable() && target != player);

        if (entityHit != null) {
            double distToEntity = entityHit.getLocation().distanceTo(start);
            if (distToEntity < distanceToTarget) distanceToTarget = distToEntity;
        }

        Vec3 actualEnd = start.add(lookAngle.scale(distanceToTarget));
        int huePulse = (int) (Math.sin(gameTime * 0.8) * 30);
        int segments = 32;

        for (int i = 0; i < segments; i++) {
            double step = (double) i / segments;
            double nextStep = (double) (i + 1) / segments;
            Vec3 p1 = start.lerp(actualEnd, step);
            Vec3 p2 = start.lerp(actualEnd, nextStep);
            float wave = (float) Math.sin((step * 16.0) - (gameTime * 0.6));
            float widthMod = 1.0f + (wave * 0.6f);
            float bu = 0.1f * widthMod;

            drawBeam(buffer, matrix, p1, p2, bu * 8, 150, 0, 0, 30);
            drawBeam(buffer, matrix, p1, p2, bu * 7, 200, 0, 0, 60);
            drawBeam(buffer, matrix, p1, p2, bu * 6, 255, 80 + huePulse, 0, 90);
            drawBeam(buffer, matrix, p1, p2, bu * 3, 255, 230 + huePulse, 50, 150);
            drawBeam(buffer, matrix, p1, p2, bu * 1, 255, 255, 210, 255);
        }

        drawImpact(buffer, matrix, actualEnd, gameTime, huePulse);
    }

    private static void renderHoldBeam(net.minecraft.world.entity.player.Player player, BufferBuilder buffer,
                                       Matrix4f matrix, float partialTick, double gameTime, int rgb, double range) {
        Vec3 start = player.getEyePosition(partialTick).add(0, -0.05, 0);
        Vec3 lookAngle = player.getViewVector(partialTick);
        Vec3 maxEndPos = start.add(lookAngle.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                start, maxEndPos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        double distanceToTarget = blockHit.getLocation().distanceTo(start);

        AABB searchBox = player.getBoundingBox().expandTowards(lookAngle.scale(range)).inflate(1.0D);
        EntityHitResult entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(
                player.level(), player, start, maxEndPos, searchBox,
                (target) -> !target.isSpectator() && target.isPickable() && target != player);

        if (entityHit != null) {
            double distToEntity = entityHit.getLocation().distanceTo(start);
            if (distToEntity < distanceToTarget) distanceToTarget = distToEntity;
        }

        Vec3 actualEnd = start.add(lookAngle.scale(distanceToTarget));
        int huePulse = (int) (Math.sin(gameTime * 0.8) * 30);
        int segments = 32;

        int r = rgb >> 16 & 0xFF;
        int g = rgb >> 8 & 0xFF;
        int b = rgb & 0xFF;

        for (int i = 0; i < segments; i++) {
            double step = (double) i / segments;
            double nextStep = (double) (i + 1) / segments;
            Vec3 p1 = start.lerp(actualEnd, step);
            Vec3 p2 = start.lerp(actualEnd, nextStep);
            float wave = (float) Math.sin((step * 16.0) - (gameTime * 0.6));
            float widthMod = 1.0f + (wave * 0.6f);
            float bu = 0.1f * widthMod;

            drawBeam(buffer, matrix, p1, p2, bu * 8, r * 3 / 5, g * 3 / 5, b * 3 / 5, 30);
            drawBeam(buffer, matrix, p1, p2, bu * 7, r * 4 / 5, g * 4 / 5, b * 4 / 5, 60);
            drawBeam(buffer, matrix, p1, p2, bu * 6, r, Math.min(255, g + huePulse), b, 90);
            drawBeam(buffer, matrix, p1, p2, bu * 3, r, Math.min(255, g + 80 + huePulse), Math.min(255, b + 30), 150);
            drawBeam(buffer, matrix, p1, p2, bu * 1, 255, Math.min(255, g + 100), Math.min(255, b + 150), 255);
        }

        drawHoldImpact(buffer, matrix, actualEnd, gameTime, huePulse, r, g, b);
    }

    private static void drawHoldImpact(BufferBuilder buffer, Matrix4f matrix, Vec3 point, double gameTime, int huePulse, int cr, int cg, int cb) {
        float baseSize = 0.3f;
        float pulse = (float) Math.abs(Math.sin(gameTime * 4.0)) * 0.3f;
        float flicker = baseSize + pulse;
        float rotation = (float) (gameTime * 0.5);
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        int r = cr, g = Math.min(255, cg + 80 + huePulse), b = Math.min(255, cb + 60), a = 220;

        for (int axis = 0; axis < 3; axis++) {
            float off1 = flicker * cos;
            float off2 = flicker * sin;
            float x1 = (float) point.x - (axis == 0 ? 0 : off1);
            float x2 = (float) point.x + (axis == 0 ? 0 : off1);
            float y1 = (float) point.y - (axis == 1 ? 0 : off2);
            float y2 = (float) point.y + (axis == 1 ? 0 : off2);
            float z1 = (float) point.z - (axis == 2 ? 0 : off1);
            float z2 = (float) point.z + (axis == 2 ? 0 : off1);

            buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2 + (off2 * 0.2f), y2 + (off1 * 0.2f), z2 + (off2 * 0.2f)).setColor(r, g, b, 0);
            buffer.addVertex(matrix, x1 - (off2 * 0.2f), y1 - (off1 * 0.2f), z1 - (off2 * 0.2f)).setColor(r, g, b, 0);
        }

        float glowSize = flicker * 2.5f;
        drawImpactGlow(buffer, matrix, point, glowSize, cr, cg / 2, cb / 3, 40);
        drawImpactGlow(buffer, matrix, point, glowSize * 0.5f, 255, Math.min(255, cg + 120), Math.min(255, cb + 100), 100);
    }

    private static void drawBeam(BufferBuilder buffer, Matrix4f matrix, Vec3 start, Vec3 end,
                                  float width, int r, int g, int b, int a) {
        float h = width / 2.0f;
        Vec3 direction = end.subtract(start).normalize();
        Vec3 side = direction.cross(new Vec3(0, 1, 0)).normalize();
        if (side.lengthSqr() < 0.001) side = direction.cross(new Vec3(1, 0, 0)).normalize();
        Vec3 up = direction.cross(side).normalize();
        side = side.scale(h);
        up = up.scale(h);

        // vertical quad.
        buffer.addVertex(matrix, (float)(start.x - up.x), (float)(start.y - up.y), (float)(start.z - up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(end.x - up.x), (float)(end.y - up.y), (float)(end.z - up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(end.x + up.x), (float)(end.y + up.y), (float)(end.z + up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(start.x + up.x), (float)(start.y + up.y), (float)(start.z + up.z)).setColor(r, g, b, a);

        // horizontal quad.
        buffer.addVertex(matrix, (float)(start.x - side.x), (float)(start.y - side.y), (float)(start.z - side.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(end.x - side.x), (float)(end.y - side.y), (float)(end.z - side.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(end.x + side.x), (float)(end.y + side.y), (float)(end.z + side.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(start.x + side.x), (float)(start.y + side.y), (float)(start.z + side.z)).setColor(r, g, b, a);
    }

    private static void drawImpact(BufferBuilder buffer, Matrix4f matrix, Vec3 point, double gameTime, int huePulse) {
        float baseSize = 0.3f;
        float pulse = (float) Math.abs(Math.sin(gameTime * 4.0)) * 0.3f;
        float flicker = baseSize + pulse;
        float rotation = (float) (gameTime * 0.5);
        float cos = (float) Math.cos(rotation);
        float sin = (float) Math.sin(rotation);
        int r = 255, g = Math.min(255, 200 + huePulse), b = 120, a = 220;

        for (int axis = 0; axis < 3; axis++) {
            float off1 = flicker * cos;
            float off2 = flicker * sin;
            float x1 = (float) point.x - (axis == 0 ? 0 : off1);
            float x2 = (float) point.x + (axis == 0 ? 0 : off1);
            float y1 = (float) point.y - (axis == 1 ? 0 : off2);
            float y2 = (float) point.y + (axis == 1 ? 0 : off2);
            float z1 = (float) point.z - (axis == 2 ? 0 : off1);
            float z2 = (float) point.z + (axis == 2 ? 0 : off1);

            buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a);
            buffer.addVertex(matrix, x2 + (off2 * 0.2f), y2 + (off1 * 0.2f), z2 + (off2 * 0.2f)).setColor(r, g, b, 0);
            buffer.addVertex(matrix, x1 - (off2 * 0.2f), y1 - (off1 * 0.2f), z1 - (off2 * 0.2f)).setColor(r, g, b, 0);
        }

        float glowSize = flicker * 2.5f;
        drawImpactGlow(buffer, matrix, point, glowSize, 255, 50, 0, 40);
        drawImpactGlow(buffer, matrix, point, glowSize * 0.5f, 255, 200, 100, 100);
    }

    private static void drawImpactGlow(BufferBuilder buffer, Matrix4f matrix, Vec3 pos,
                                       float size, int r, int g, int b, int a) {
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 dir = pos.subtract(cam).normalize();
        float shimmer = 1.0f + (float) Math.sin(System.currentTimeMillis() / 100.0) * 0.1f;
        float finalSize = size * shimmer;
        Vec3 right = dir.cross(new Vec3(0, 1, 0)).normalize().scale(finalSize);
        Vec3 up = dir.cross(right).normalize().scale(finalSize);

        buffer.addVertex(matrix, (float)(pos.x - right.x - up.x), (float)(pos.y - right.y - up.y), (float)(pos.z - right.z - up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(pos.x + right.x - up.x), (float)(pos.y + right.y - up.y), (float)(pos.z + right.z - up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(pos.x + right.x + up.x), (float)(pos.y + right.y + up.y), (float)(pos.z + right.z + up.z)).setColor(r, g, b, a);
        buffer.addVertex(matrix, (float)(pos.x - right.x + up.x), (float)(pos.y - right.y + up.y), (float)(pos.z - right.z + up.z)).setColor(r, g, b, a);
    }
}
