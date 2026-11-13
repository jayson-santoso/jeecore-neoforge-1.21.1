package net.sonflowers.jeecore.gene.infernal.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;

/**
 * Renders the local smoke resource HUD from synchronized Infernal ability state.
 */
public class InfernalSmokeBarRenderer implements LayeredDraw.Layer {
    private static final int BAR_WIDTH = 8;
    private static final int BAR_HEIGHT = 54;
    private static final int RIGHT_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 14;

    private static float displayedResource = -1.0f;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (GeneHelper.getPlayerGene(mc.player) != Gene.INFERNAL) return;

        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int targetResource = data.getSmokeResource();
        int maxResource = data.getMaxSmokeResource();
        if (displayedResource < 0.0f) {
            displayedResource = targetResource;
        }

        float deltaTicks = Math.max(0.0f, deltaTracker.getGameTimeDeltaTicks());
        displayedResource = approach(displayedResource, targetResource, Math.max(1.0f, 18.0f * deltaTicks));

        float fillPercent = maxResource > 0 ? displayedResource / maxResource : 0.0f;
        fillPercent = Math.max(0.0f, Math.min(1.0f, fillPercent));

        int x = mc.getWindow().getGuiScaledWidth() - RIGHT_MARGIN - BAR_WIDTH;
        int y = mc.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - BAR_HEIGHT;
        int fillHeight = Math.round(BAR_HEIGHT * fillPercent);
        int fillTop = y + BAR_HEIGHT - fillHeight;

        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xAA000000);
        boolean inStopCooldown = data.getSmokeCooldownTicks() > 0;
        boolean isRaging = InfernalRageManager.isRaging(mc.player);

        // Grey out when raging (can't use smoke form during rage).
        int borderColor = isRaging ? 0xFF777777 : (inStopCooldown ? 0xFF777777 : 0xFFFFB04A);
        int fillColor = isRaging ? 0xFF8A8A8A : (inStopCooldown ? 0xFF555555 : (targetResource < 12 ? 0xFFFF5555 : 0xFFFF8A1C));

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, borderColor);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC14100C);
        if (fillHeight > 0) {
            graphics.fill(x, fillTop, x + BAR_WIDTH, y + BAR_HEIGHT, fillColor);
        }
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(target, current + maxStep);
        if (current > target) return Math.max(target, current - maxStep);
        return current;
    }
}
