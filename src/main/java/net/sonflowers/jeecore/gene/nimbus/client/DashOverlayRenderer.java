package net.sonflowers.jeecore.gene.nimbus.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;

/**
 * Renders the local dash overlay while the server-synchronized dash flag is active.
 */
public class DashOverlayRenderer implements LayeredDraw.Layer {
    private static boolean active = false;
    private static final ResourceLocation NAUSEA_TEXTURE = ResourceLocation.withDefaultNamespace("textures/misc/nausea.png");

    public static void setActive(boolean value) {
        active = value;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        boolean smokeForm = mc.player != null && SmokeManager.isSmoking(mc.player.getUUID());
        boolean glideBoost = GlideBoostRenderer.isLocalBoostActive();
        if (!active && !smokeForm && !glideBoost) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (smokeForm) {
            RenderSystem.setShaderColor(1.0f, 0.52f, 0.08f, 0.24f);
        } else if (glideBoost) {
            RenderSystem.setShaderColor(0.88f, 0.97f, 1.0f, 0.13f);
        } else {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.15f);
        }

        graphics.blit(NAUSEA_TEXTURE, 0, 0, 0, 0, width, height, width, height);

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }
}
