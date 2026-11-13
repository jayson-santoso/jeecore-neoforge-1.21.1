package net.sonflowers.jeecore.core.client;

import net.sonflowers.jeecore.core.network.RequestCooldownSyncPayload;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.resources.ResourceLocation;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.server.InfernalChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;
import net.sonflowers.jeecore.gene.nimbus.client.NimbusGustClient;
import net.sonflowers.jeecore.gene.terragolem.client.TerragolemReadyUpRenderer;

/**
 * Renders client ability cooldowns and gene-specific availability indicators from synchronized
 * state.
 */
public class AbilityCooldownRenderer implements LayeredDraw.Layer {
    private static final int SIZE = 24;
    private static final int GAP = 4;
    private static final int BOTTOM_MARGIN = 38;
    private static final ResourceLocation DRYAD_PRIMARY_ICON = ResourceLocation.fromNamespaceAndPath(
            "jeecore", "textures/gui/hud/dryad_primary.png");
    private static final ResourceLocation DRYAD_SECONDARY_ICON = ResourceLocation.fromNamespaceAndPath(
            "jeecore", "textures/gui/hud/dryad_secondary.png");
    private static final ResourceLocation INFERNAL_SECONDARY_ICON = ResourceLocation.fromNamespaceAndPath(
            "jeecore", "textures/gui/hud/infernal_secondary.png");
    private static double ignitionQuickTimeStartGameTime = 0.0;
    private static int ignitionQuickTimeZoneStart = -1;
    private static int ignitionQuickTimeZoneWidth = -1;

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;
        Gene gene = GeneHelper.getPlayerGene(mc.player);
        if (gene == Gene.NONE) return;

        // Request sync from server if client state isn't initialized yet.
        if (!ClientHudState.isInitialized()) {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(
                new net.sonflowers.jeecore.core.network.RequestCooldownSyncPayload()
            );
        }

        int totalWidth = SIZE * 2 + GAP;
        int x = mc.getWindow().getGuiScaledWidth() / 4 - totalWidth / 2;
        int y = mc.getWindow().getGuiScaledHeight() - BOTTOM_MARGIN - SIZE;
        double gameTime = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);

        boolean isRaging = gene == Gene.INFERNAL && InfernalRageManager.isRaging(mc.player);

        if (gene == Gene.INFERNAL) {
            renderIgnitionCharge(graphics, mc, deltaTracker, x, y, isRaging);
            renderRageBar(graphics, mc, deltaTracker, x + SIZE + GAP, y, gameTime, isRaging);
        } else {
            // During terragolem ready-up, the post-release aftermath, and the overheat debuff lock
            // the primary box is shown grayscale (disabled) without a countdown.
            boolean readyUp = gene == Gene.TERRAGOLEM
                    && (TerragolemReadyUpRenderer.isReadyUp(mc.player.getUUID())
                        || TerragolemReadyUpRenderer.isAftermathActive()
                        || mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA).isOverheatPrimaryLocked());
            if (gene.getPrimaryCooldown() > 0) {
                if (gene == Gene.DRYAD) {
                    renderIconCooldown(graphics, mc, x, y, gameTime, DRYAD_PRIMARY_ICON, ClientHudState.getPrimaryCooldown());
                } else {
                    renderCooldown(graphics, mc, x, y, "P", ClientHudState.getPrimaryCooldown(), gameTime, getPrimaryReadyColor(gene), gene, readyUp);
                }
            }
            if (gene.getSecondaryCooldown() > 0) {
                if (gene == Gene.NIMBUS && NimbusGustClient.isCharging()) {
                    renderGustCharge(graphics, x + SIZE + GAP, y, gameTime);
                } else if (gene == Gene.DRYAD) {
                    renderIconCooldown(graphics, mc, x + SIZE + GAP, y, gameTime, DRYAD_SECONDARY_ICON, ClientHudState.getSecondaryCooldown());
                } else {
                    renderCooldown(graphics, mc, x + SIZE + GAP, y, "S", ClientHudState.getSecondaryCooldown(), gameTime, getSecondaryReadyColor(gene), gene, false);
                }
            }
        }
    }

    private static void renderIconCooldown(GuiGraphics graphics, Minecraft mc, int x, int y, double gameTime,
                                           ResourceLocation icon, ClientHudState.CooldownState cooldown) {
        boolean ready = cooldown.ready(gameTime);
        float progress = Math.max(0, Math.min(1, cooldown.progress(gameTime)));
        renderIconFill(graphics, icon, x, y, progress, ready);
        if (!ready) {
            float seconds = cooldown.remainingTicks(gameTime) / 20f;
            String text = seconds >= 10 ? Integer.toString((int) Math.ceil(seconds)) : String.format("%.1f", seconds);
            graphics.drawString(mc.font, text, x + (SIZE - mc.font.width(text)) / 2,
                    y + (SIZE - mc.font.lineHeight) / 2, 0xFFFFFFFF, true);
        }
    }

    private static void renderIconFill(GuiGraphics graphics, ResourceLocation icon, int x, int y,
                                      float progress, boolean ready) {
        float brightness = ready ? 1 : 0.35f;
        graphics.setColor(brightness, brightness, brightness, 1);
        blitAbilityIcon(graphics, icon, x, y);
        graphics.setColor(1, 1, 1, 1);
        int fillHeight = Math.round(SIZE * Math.max(0, Math.min(1, progress)));
        if (!ready && fillHeight > 0) {
            graphics.enableScissor(x, y + SIZE - fillHeight, x + SIZE, y + SIZE);
            blitAbilityIcon(graphics, icon, x, y);
            graphics.disableScissor();
        }
    }

    private static void blitAbilityIcon(GuiGraphics graphics, ResourceLocation icon, int x, int y) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(SIZE / 16f, SIZE / 16f, 1);
        graphics.blit(icon, 0, 0, 0, 0, 16, 16, 16, 16);
        graphics.pose().popPose();
    }

    private static void renderGustCharge(GuiGraphics graphics, int x, int y, double gameTime) {
        float progress = NimbusGustClient.chargeProgress(gameTime);
        int fillHeight = Math.round((SIZE - 4) * progress);
        int fillTop = y + SIZE - 2 - fillHeight;
        int readyColor = getSecondaryReadyColor(Gene.NIMBUS);
        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xAA000000);
        graphics.fill(x, y, x + SIZE, y + SIZE, 0xFFFFFFFF);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, darken(readyColor, 0.18f));
        graphics.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, darken(readyColor, 0.34f));
        if (fillHeight > 0) {
            graphics.fill(x + 2, fillTop, x + SIZE - 2, y + SIZE - 2, readyColor);
        }
    }

    private static void renderRageBar(GuiGraphics graphics, Minecraft mc, DeltaTracker deltaTracker, int x, int y, double gameTime, boolean isRaging) {
        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int rageBar = data.getRageBar();
        int maxRage = data.getMaxRageBar();
        boolean ready = rageBar >= maxRage;
        float progress = maxRage > 0 ? (float) rageBar / maxRage : 0.0f;
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        int shakeX = 0, shakeY = 0;
        if (isRaging) {
            float shakeIntensity = 2.0f;
            shakeX = (int) (Math.sin(gameTime * 0.8) * shakeIntensity);
            shakeY = (int) (Math.cos(gameTime * 0.6) * shakeIntensity);
        }

        if (isRaging) {
            renderPounceCharges(graphics, x + shakeX, y + shakeY);
        }
        renderIconFill(graphics, INFERNAL_SECONDARY_ICON, x + shakeX, y + shakeY, progress, ready);

        // Keep the percentage above the icon so the artwork stays visible.
        String pctText = (int)(progress * 100) + "%";
        int textColor = ready ? 0xFFFFFFFF : 0xFFCCCCCC;

        // Match the compact pixel HUD with a small centered label above it.
        float scale = 0.75f;
        int centerX = x + shakeX + SIZE / 2;
        int centerY = y + shakeY - 6;

        graphics.pose().pushPose();
        // Translate to center, then scale, then offset by half text size.
        graphics.pose().translate(centerX, centerY, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().translate(-mc.font.width(pctText) / 2.0f, -mc.font.lineHeight / 2.0f, 0);

        // Draw shadow for better contrast (bold effect through double rendering).
        graphics.drawString(mc.font, pctText, 1, 1, 0xFF000000, false);
        graphics.drawString(mc.font, pctText, 0, 0, textColor, false);
        // Draw again slightly offset for bold effect.
        graphics.drawString(mc.font, pctText, 1, 0, textColor, false);

        graphics.pose().popPose();
    }

    private static void renderPounceCharges(GuiGraphics graphics, int x, int y) {
        int left = x + SIZE + 3;
        int width = 10;
        // The three-row frame matches the icon's height and uses colors sampled from its artwork.
        graphics.fill(left - 1, y - 1, left + width + 1, y + SIZE + 1, 0xAA000000);
        graphics.fill(left, y, left + width, y + SIZE, 0xFF171A16);
        graphics.fill(left + 1, y + 1, left + width - 1, y + SIZE - 1, 0xFF802C24);
        graphics.fill(left + 1, y + 1, left + width - 1, y + 2, 0xFFBD4949);
        graphics.fill(left + 2, y + 2, left + width - 2, y + SIZE - 2, 0xFF171A16);
        int charges = ClientHudState.getPounceCharges();
        for (int row = 0; row < 3; row++) {
            int rowTop = y + 2 + row * 7;
            boolean filled = row >= 3 - charges;
            graphics.fill(left + 2, rowTop, left + width - 2, rowTop + 6,
                    filled ? 0xFFED4040 : 0xFF302C2A);
            if (filled) {
                graphics.fill(left + 2, rowTop, left + width - 2, rowTop + 1, 0xFFFF6767);
                graphics.fill(left + 2, rowTop + 5, left + width - 2, rowTop + 6, 0xFF912525);
            }
        }
    }

    private static void renderCooldown(
            GuiGraphics graphics,
            Minecraft mc,
            int x,
            int y,
            String label,
            ClientHudState.CooldownState cooldown,
            double gameTime,
            int readyColor,
            Gene gene,
            boolean disabled
    ) {
        boolean ready = !disabled && cooldown.ready(gameTime);
        float progress = disabled ? 0.0f : Math.max(0.0f, Math.min(1.0f, cooldown.progress(gameTime)));
        int fillHeight = Math.round((SIZE - 4) * progress);
        int fillTop = y + SIZE - 2 - fillHeight;

        // Use white border for both primary and secondary (consistency).
        int border = ready ? 0xFFFFFFFF : 0xFF3A3A3A;
        int back = ready ? darken(readyColor, 0.18f) : 0xFF3A3A3A;
        int well = ready ? darken(readyColor, 0.34f) : 0xFF555555;

        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xAA000000);
        graphics.fill(x, y, x + SIZE, y + SIZE, border);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, back);
        graphics.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, well);
        if (fillHeight > 0) {
            graphics.fill(x + 2, fillTop, x + SIZE - 2, y + SIZE - 2, ready ? readyColor : 0xFF8A8A8A);
        }

        String text;
        int color;
        if (ready) {
            text = label;
            color = 0xFFFFFFFF;
        } else if (disabled) {
            // Disabled state (e.g. terragolem ready-up): grayscale box, no countdown number.
            text = label;
            color = 0xFF777777;
        } else {
            float seconds = cooldown.remainingTicks(gameTime) / 20.0f;
            text = seconds >= 10.0f ? Integer.toString((int) Math.ceil(seconds)) : String.format("%.1f", seconds);
            color = 0xFFECECEC;
        }

        int textX = x + (SIZE - mc.font.width(text)) / 2;
        int textY = y + (SIZE - mc.font.lineHeight) / 2;
        graphics.drawString(mc.font, text, textX, textY, color, true);
    }

    private static int getPrimaryReadyColor(Gene gene) {
        return switch (gene) {
            case DRYAD -> 0xFFFFD700; // Bright yellow/gold for elixir.
            case NIMBUS -> 0xFF87CEEB;
            case TERRAGOLEM -> 0xFFCD853F; // Sandy brown for ready-up.
            default -> 0xFFE9F6FF;
        };
    }

    private static int getSecondaryReadyColor(Gene gene) {
        return switch (gene) {
            case DRYAD -> 0xFF90EE90; // Light green for root snare.
            case NIMBUS -> 0xFF87CEEB;
            default -> 0xFFE9F6FF;
        };
    }

    private static int darken(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >>> 16) & 0xFF) * factor);
        int g = (int) (((argb >>> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void renderIgnitionCharge(GuiGraphics graphics, Minecraft mc, DeltaTracker deltaTracker, int x, int y, boolean isRaging) {
        PlayerGeneData data = mc.player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean enabled = data.isIgnitionChargeEnabled();
        boolean cooldown = data.getIgnitionCooldownTicks() > 0;
        boolean ready = data.getIgnitionCharge() >= data.getMaxIgnitionCharge();
        float progress = data.getMaxIgnitionCharge() > 0
                ? data.getIgnitionCharge() / (float) data.getMaxIgnitionCharge()
                : 0.0f;
        progress = Math.max(0.0f, Math.min(1.0f, progress));

        int fillHeight = Math.round((SIZE - 4) * progress);
        int fillTop = y + SIZE - 2 - fillHeight;

        // Grey out when raging (can't use ignition charge during rage).
        int border = isRaging ? 0xFF777777 : (enabled && !cooldown ? 0xFFFF9A2F : 0xFF777777);
        int back = isRaging ? 0xFF222222 : (enabled ? 0xFF2E2722 : 0xFF222222);
        int well = isRaging ? 0xFF555555 : (cooldown || !enabled ? 0xFF555555 : 0xFF3D342E);
        int fill = isRaging ? 0xFF8A8A8A : (ready ? 0xFFFF4A1C : 0xFF8A8A8A);

        graphics.fill(x - 1, y - 1, x + SIZE + 1, y + SIZE + 1, 0xAA000000);
        graphics.fill(x, y, x + SIZE, y + SIZE, border);
        graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, back);
        graphics.fill(x + 2, y + 2, x + SIZE - 2, y + SIZE - 2, well);
        if (fillHeight > 0) {
            graphics.fill(x + 2, fillTop, x + SIZE - 2, y + SIZE - 2, fill);
        }

        String stateText = enabled ? "ON" : "OFF";
        int stateColor = enabled ? 0xFF90EE90 : 0xFFFF6666;
        int stateX = x + (SIZE - mc.font.width(stateText)) / 2;
        int stateY = y - 11;
        graphics.drawString(mc.font, stateText, stateX + 1, stateY, stateColor, true);
        graphics.drawString(mc.font, stateText, stateX, stateY, stateColor, true);

        if (data.isIgnitionQuickTimeActive() && ready) {
            int centerX = mc.getWindow().getGuiScaledWidth() / 2;
            int centerY = mc.getWindow().getGuiScaledHeight() / 2;
            double gameTime = mc.level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(true);
            renderQuickTimeBar(graphics, data, gameTime, centerX - 50, centerY + 13);
        } else {
            ignitionQuickTimeZoneStart = -1;
            ignitionQuickTimeZoneWidth = -1;
        }
    }

    private static void renderQuickTimeBar(GuiGraphics graphics, PlayerGeneData data, double gameTime, int x, int y) {
        int width = 100;
        int height = 8;
        int zoneX = x + 1 + Math.round((width - 2) * data.getIgnitionQuickTimeZoneStart() / 100.0f);
        int zoneWidth = Math.max(2, Math.round((width - 2) * data.getIgnitionQuickTimeZoneWidth() / 100.0f));
        if (ignitionQuickTimeZoneStart != data.getIgnitionQuickTimeZoneStart()
                || ignitionQuickTimeZoneWidth != data.getIgnitionQuickTimeZoneWidth()) {
            ignitionQuickTimeStartGameTime = gameTime - data.getIgnitionTimer();
            ignitionQuickTimeZoneStart = data.getIgnitionQuickTimeZoneStart();
            ignitionQuickTimeZoneWidth = data.getIgnitionQuickTimeZoneWidth();
        }
        float pointer = InfernalChargeManager.getQuickTimePointer(gameTime - ignitionQuickTimeStartGameTime);
        int pointerX = x + 1 + Math.round((width - 2) * pointer);

        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xAA000000);
        graphics.fill(x, y, x + width, y + height, 0xFF2A2420);
        graphics.fill(zoneX, y + 1, Math.min(x + width - 1, zoneX + zoneWidth), y + height - 1, 0xFFFFD35A);
        graphics.fill(pointerX - 1, y - 2, pointerX + 2, y + height + 2, 0xFFFFFFFF);
    }
}

