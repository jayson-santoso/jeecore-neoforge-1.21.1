package net.sonflowers.jeecore.gene.terragolem.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;

/**
 * Renders the local heat HUD with smoothed draining and synchronized overheat warnings.
 */
public class TerragolemOverheatBarRenderer implements LayeredDraw.Layer {
    private static final int BAR_WIDTH = 8;
    private static final int BAR_HEIGHT = 54;
    private static final int RIGHT_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 14;

    private static float displayedHeat = -1.0f;
    private static int previousTargetHeat = -1;
    private static boolean fastCooling;
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        if (GeneHelper.getPlayerGene(mc.player) != Gene.TERRAGOLEM) return;

        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int targetHeat = data.getOverheatBar();
        int maxHeat = data.getMaxOverheatBar();
        if (previousLevel != mc.level) {
            previousLevel = mc.level;
            displayedHeat = -1;
            previousTargetHeat = -1;
            fastCooling = false;
        }
        if (previousTargetHeat >= 0 && previousTargetHeat - targetHeat >= maxHeat * 0.1f) {
            fastCooling = true;
        }
        previousTargetHeat = targetHeat;
        if (displayedHeat < 0.0f) {
            displayedHeat = targetHeat;
        }

        float deltaTicks = Math.max(0.0f, deltaTracker.getGameTimeDeltaTicks());
        // Fuel cooling responds promptly; passive heat loss retains its smooth drain.
        float step = targetHeat >= displayedHeat
                ? Math.max(1.0f, 18.0f * deltaTicks)
                : (fastCooling ? 32.0f : 1.5f) * deltaTicks;
        displayedHeat = approach(displayedHeat, targetHeat, step);
        if (displayedHeat <= targetHeat) fastCooling = false;

        float fillPercent = maxHeat > 0 ? displayedHeat / maxHeat : 0.0f;
        fillPercent = Math.max(0.0f, Math.min(1.0f, fillPercent));

        double gameTime = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(true);

        int x = mc.getWindow().getGuiScaledWidth() - RIGHT_MARGIN - BAR_WIDTH;
        int y = mc.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - BAR_HEIGHT;
        int fillHeight = Math.round(BAR_HEIGHT * fillPercent);

        graphics.fill(x - 2, y - 2, x + BAR_WIDTH + 2, y + BAR_HEIGHT + 2, 0xAA000000);

        // Warn from 75%; after overheating, use a steady slow pulse throughout cooldown.
        int borderColor = 0xFFFFFFFF;
        float actualHeatPercent = maxHeat > 0 ? targetHeat / (float) maxHeat : 0.0f;
        if (actualHeatPercent >= 0.75f || data.isOverheatPrimaryLocked()) {
            float flash = warningFlash(gameTime, actualHeatPercent, data.isOverheatPrimaryLocked());
            int gb = 255 - (int) (215 * flash);
            borderColor = 0xFF000000 | (255 << 16) | (gb << 8) | gb;
        }

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, borderColor);
        graphics.fill(x, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xCC14100C);

        // Gradient fill: light orange at the bottom -> deep red pink at the top.
        for (int i = 0; i < fillHeight; i++) {
            float t = fillHeight <= 1 ? 1.0f : (float) i / (fillHeight - 1);
            graphics.fill(x, y + BAR_HEIGHT - 1 - i, x + BAR_WIDTH, y + BAR_HEIGHT - i, overheatGradient(t));
        }
    }

    public static float warningFlash(double gameTime, float heatPercent, boolean overheated) {
        float heatZone = Math.max(0, Math.min(1, (heatPercent - 0.75f) / 0.25f));
        float speed = overheated ? 0.16f : 0.3f + 1.6f * heatZone;
        return (float) Math.sin(gameTime * speed) * 0.5f + 0.5f;
    }

    // t: 0 = bottom (light orange) -> 1 = top (deep red pink).
    private static int overheatGradient(float t) {
        int r = (int) (255 + (215 - 255) * t);
        int g = (int) (180 + (25 - 180) * t);
        int b = (int) (90 + (95 - 90) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static float approach(float current, float target, float maxStep) {
        if (current < target) return Math.min(target, current + maxStep);
        if (current > target) return Math.max(target, current - maxStep);
        return current;
    }
}
