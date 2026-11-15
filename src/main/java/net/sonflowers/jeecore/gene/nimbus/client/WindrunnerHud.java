package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import java.io.IOException;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerState;

/**
 * Renders the local momentum HUD and transition feedback from owner-only synchronized Windrunner
 * state.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public final class WindrunnerHud implements LayeredDraw.Layer {
    private static final ResourceLocation NAUSEA = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");
    private static final ResourceLocation WHITE_NAUSEA = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "windrunner_white_nausea");
    private static boolean overlayReady;
    private WindrunnerState owner;
    private float displayed;

    private static boolean visible(Minecraft mc) {
        return mc.player != null && mc.player.isAlive() && !mc.player.isSpectator()
                && GeneHelper.getPlayerGene(mc.player) == Gene.NIMBUS;
    }

    private static float age(Minecraft mc, WindrunnerState s, double partial) {
        return (float)(mc.player.level().getGameTime() - s.transitionTick + partial);
    }

    private static float pulse(float age, float duration) {
        if (age < 0 || age >= duration) return 0;
        float t = age / duration;
        // Fast onset, eased return; deterministic at every frame rate.
        if (t < .22f) return t / .22f;
        float remaining = (1 - t) / .78f;
        return remaining * remaining;
    }

    private static float promotionPulse(float age, float duration) {
        if (age < 0 || age >= duration) return 0;
        float t = age / duration;
        float phase = t < .4f ? t / .4f : (1 - t) / .6f;
        // Zero slope at onset, peak, and finish: a smooth swell instead of a sharp kick.
        return phase * phase * (3 - 2 * phase);
    }

    private static boolean prepareWhiteOverlay(Minecraft mc) {
        if (overlayReady) return true;
        try (var stream = mc.getResourceManager().open(NAUSEA)) {
            NativeImage image = NativeImage.read(stream);
            for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                int pixel = image.getPixelRGBA(x, y);
                int brightness = Math.max(pixel & 255, Math.max(pixel >>> 8 & 255, pixel >>> 16 & 255));
                int alpha = brightness * (pixel >>> 24) / 255;
                // Preserve the nausea pattern as opacity, with white RGB and a clear center.
                image.setPixelRGBA(x, y, (alpha << 24) | 0xFFFFFF);
            }
            mc.getTextureManager().register(WHITE_NAUSEA, new DynamicTexture(image));
            overlayReady = true;
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    @SubscribeEvent public static void fov(ViewportEvent.ComputeFov event) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc) || !event.usedConfiguredFov()) return;
        var s = mc.player.getData(GeneDataAttachment.WINDRUNNER);
        if (s.promotionStage <= 0) return;
        double scale = mc.options.fovEffectScale().get();
        float promotionAge = (float)(mc.player.level().getGameTime() - s.promotionTick + event.getPartialTick());
        event.setFOV(event.getFOV() + (2 + 2 * s.promotionStage) * scale
                * promotionPulse(promotionAge, 12 + 4 * s.promotionStage));
    }

    @Override public void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!visible(mc)) { owner = null; displayed = 0; return; }
        var s = mc.player.getData(GeneDataAttachment.WINDRUNNER);
        if (owner != s) { owner = s; displayed = s.clientMomentum; }
        float partial = delta.getGameTimeDeltaPartialTick(true);
        float age = age(mc, s, partial);
        if (!mc.options.hideGui && s.promotionStage > 0) {
            float promotionAge = mc.player.level().getGameTime() - s.promotionTick + partial;
            float alpha = promotionPulse(promotionAge, 6 + 2 * s.promotionStage) * (.15f + .07f * s.promotionStage)
                    * mc.options.screenEffectScale().get().floatValue();
            if (alpha > .001f && prepareWhiteOverlay(mc)) {
                int w = g.guiWidth(), h = g.guiHeight();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(.96f, .99f, 1, alpha);
                g.blit(WHITE_NAUSEA, 0, 0, 0, 0, w, h, w, h);
                g.flush();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.disableBlend();
            }
        }
        if (mc.options.hideGui) return;
        float interpolation = 1 - (float)Math.exp(-.35 * Math.max(0, delta.getGameTimeDeltaTicks()));
        if (s.transition < 0 && displayed > s.clientMomentum) displayed = s.clientMomentum;
        else displayed += (s.clientMomentum - displayed) * interpolation;
        int x = g.guiWidth() - 84, y = g.guiHeight() - 80;
        float flash = pulse(age, 10);
        for (int i = 0; i < 3; i++) {
            int sx = x + i * 19;
            float fill = Math.clamp((displayed - i * 25) / 25, 0, 1);
            boolean earned = i < s.clientStage;
            int tint = s.clientDecaying ? 0x7FABB9 : 0xB5EDFF;
            int opacity = earned ? 195 : 115;
            boolean changing = s.transition > 0 ? i == s.clientStage - 1 : i == s.clientStage;
            if (changing && flash > 0) {
                opacity = (int)(opacity + (255 - opacity) * flash);
                if (s.transition > 0) tint = 0xF0FCFF;
            }
            g.fill(sx - 1, y - 1, sx + 17, y + 4, earned ? 0x665B94A6 : 0x33152229);
            g.fill(sx, y, sx + 16, y + 3, 0x55203943);
            if (fill > 0) g.fill(sx, y, sx + Math.round(16 * fill), y + 3, (opacity << 24) | tint);
            if (earned) g.fill(sx, y - 1, sx + 16, y, 0x99C4F1FF);
        }
    }
}
