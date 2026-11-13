package net.sonflowers.jeecore.gene.nimbus.client;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.client.KeyBindings;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.network.GustInputPayload;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusGustManager;

/**
 * Tracks local gust input and synchronized charge timing for camera and release feedback.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class NimbusGustClient {
    private static boolean keyHeld, charging;
    private static long started;
    private static float currentFov = 1, transitionFrom = 1, releasePeak = 1;
    private static double transitionStart = -100;
    private static boolean releaseJolt;
    private NimbusGustClient() {}

    public static void setCharging(boolean active, long time) {
        if (active && !charging) {
            WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, 0.18f);
            transitionFrom = currentFov;
            releaseJolt = false;
        } else if (!active && charging) {
            beginFovTransition(false, 1);
        }
        charging = active;
        started = time;
    }

    public static boolean isCharging() {
        var mc = Minecraft.getInstance();
        return charging && mc.level != null && mc.player != null && mc.player.isAlive()
                && GeneHelper.getPlayerGene(mc.player) == Gene.NIMBUS;
    }

    public static float chargeProgress(double gameTime) {
        return isCharging() ? Mth.clamp((float) ((gameTime - started) / NimbusGustManager.CHARGE_TICKS), 0, 1) : 0;
    }

    public static void onRelease(float charge) {
        beginFovTransition(true, 1.20f + 0.12f * Mth.clamp(charge, 0, 1));
    }

    private static void beginFovTransition(boolean jolt, float peak) {
        var mc = Minecraft.getInstance();
        transitionFrom = currentFov;
        transitionStart = mc.level == null ? 0 : mc.level.getGameTime();
        releaseJolt = jolt;
        releasePeak = peak;
    }

    public static float getFovMultiplier(double partialTick) {
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !mc.player.isAlive()
                || GeneHelper.getPlayerGene(mc.player) != Gene.NIMBUS) return currentFov = 1;
        double now = mc.level.getGameTime() + partialTick;
        if (isCharging()) {
            float age = (float) (now - started);
            float squeeze = smooth(age / 7);
            float target = Mth.lerp(smooth(chargeProgress(now)), 0.82f, 0.70f);
            currentFov = Mth.lerp(squeeze, transitionFrom, target);
        } else {
            float age = (float) (now - transitionStart);
            if (releaseJolt && age < 1.5f) {
                currentFov = Mth.lerp(smooth(age / 1.5f), transitionFrom, releasePeak);
            } else if (releaseJolt) {
                currentFov = Mth.lerp(smooth((age - 1.5f) / 6), releasePeak, 1);
            } else {
                currentFov = Mth.lerp(smooth(age / 5), transitionFrom, 1);
            }
        }
        return currentFov;
    }

    private static float smooth(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static void updateInput(boolean pressed) {
        var mc = Minecraft.getInstance();
        boolean valid = mc.player != null && mc.player.isAlive() && mc.screen == null && mc.isWindowActive()
                && GeneHelper.getPlayerGene(mc.player) == Gene.NIMBUS;
        if (!valid) {
            if (keyHeld || charging) PacketDistributor.sendToServer(new GustInputPayload(GustInputPayload.CANCEL));
            if (charging) beginFovTransition(false, 1);
            keyHeld = charging = false;
            return;
        }
        if (pressed && !keyHeld) {
            keyHeld = true;
            PacketDistributor.sendToServer(new GustInputPayload(GustInputPayload.PRESS));
        }
        if (keyHeld && !KeyBindings.SECONDARY_ABILITY.isDown()) {
            keyHeld = false;
            PacketDistributor.sendToServer(new GustInputPayload(GustInputPayload.RELEASE));
        } else if (keyHeld && charging && mc.player.tickCount % 10 == 0) {
            PacketDistributor.sendToServer(new GustInputPayload(GustInputPayload.HOLD));
        }
    }

    public static float chargeShake() {
        var mc = Minecraft.getInstance();
        if (!isCharging()) return 0;
        float progress = chargeProgress(mc.level.getGameTime());
        return 0.04f + 0.24f * progress * progress;
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            keyHeld = charging = false;
            WindRenderer.BURSTS.clear();
            NimbusGustChargeRenderer.clear();
            GlideBoostRenderer.clear();
            currentFov = transitionFrom = releasePeak = 1;
            transitionStart = -100;
            releaseJolt = false;
        }
    }
}
