package net.sonflowers.jeecore.gene.nimbus.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;

/**
 * Renders the local glide resource HUD from synchronized Nimbus state.
 */
public class GlideBarRenderer implements LayeredDraw.Layer {
    private static final int BAR_WIDTH = 8;
    private static final int BAR_HEIGHT = 54;
    private static final int RIGHT_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 14;

    private static float displayedResource = -1.0f;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (GeneHelper.getPlayerGene(mc.player) != Gene.NIMBUS) return;

        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int maxResource = data.getMaxGlideResource();
        int targetResource = data.getGlideResource();
        if (displayedResource < 0.0f) {
            displayedResource = targetResource;
        }

        float deltaTicks = Math.max(0.0f, deltaTracker.getGameTimeDeltaTicks());
        float maxStep = Math.max(1.0f, 24.0f * deltaTicks);
        displayedResource = approach(displayedResource, targetResource, maxStep);

        float fillPercent = maxResource > 0 ? displayedResource / maxResource : 0.0f;
        fillPercent = Math.max(0.0f, Math.min(1.0f, fillPercent));

        int x = mc.getWindow().getGuiScaledWidth() - RIGHT_MARGIN - BAR_WIDTH;
        int y = mc.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - BAR_HEIGHT;
        int fillHeight = Math.round(BAR_HEIGHT * fillPercent);
        int fillTop = y + BAR_HEIGHT - fillHeight;

        int borderColor = data.isGlideToggled() ? 0xFFFFFFFF : 0xFF777777;
        int fillColor = data.isGlideToggled() ? (targetResource < 50 ? 0xFFFF5555 : 0xFF87CEEB) : 0xFF555555;

        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xAA000000);
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, borderColor);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC14181C);
        if (fillHeight > 0) {
            graphics.fill(x, fillTop, x + BAR_WIDTH, y + BAR_HEIGHT, fillColor);
        }

        Component value = data.isGlideToggled()
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.RED, ChatFormatting.BOLD);
        Component status = Component.literal("Glide: ").append(value);
        int textX = x - mc.font.width(status) - 7;
        graphics.drawString(mc.font, status, textX, y + BAR_HEIGHT - mc.font.lineHeight, 0xFFFFFFFF, true);
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(target, current + maxStep);
        if (current > target) return Math.max(target, current - maxStep);
        return current;
    }
}
