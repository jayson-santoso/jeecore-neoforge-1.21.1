package net.sonflowers.jeecore.gene.terragolem.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.sonflowers.jeecore.gene.terragolem.menu.TerragolemRepairRound;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.terragolem.menu.TerragolemRepairMenu;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemRepairManager;

/**
 * Renders the client repair menu and sends clicks only from the visible body silhouette to the
 * server.
 */
public final class TerragolemRepairScreen extends AbstractContainerScreen<TerragolemRepairMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            JeeCore.MOD_ID, "textures/gui/terragolem_repair.png");
    private static final ResourceLocation BODY = ResourceLocation.fromNamespaceAndPath(
            JeeCore.MOD_ID, "textures/gui/terragolem_body.png");
    // Same opaque rectangles as the pixel-art texture; transparent gaps are never clickable.
    private static final int[][] BODY_PARTS = {
            {17, 0, 35, 18}, {11, 20, 41, 50}, {0, 22, 9, 54}, {43, 22, 52, 54},
            {12, 52, 25, 80}, {27, 52, 40, 80}
    };
    private BodyButton bodyButton;
    private long predictedCooldownUntil;
    private int attemptedToken = -1;
    private int feedbackToken = -1;
    private int playedFeedback;
    private long noFuelUntil;

    public TerragolemRepairScreen(TerragolemRepairMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 190;
        imageHeight = 236;
        inventoryLabelX = 14;
        inventoryLabelY = 141;
    }

    @Override protected void init() {
        super.init();
        bodyButton = addRenderableWidget(new BodyButton(leftPos + 112, topPos + 28));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var round = menu.round();
        bodyButton.active = minecraft.player != null && menu.stillValid(minecraft.player)
                && (round.active() || (menu.health() < menu.maxHealth()
                && menu.hunger() > 0 && menu.selectedRepairHearts() > 0
                && menu.cooldown() == 0 && minecraft.player.tickCount >= predictedCooldownUntil));
        bodyButton.setTooltip(round.active() ? null : Tooltip.create(Component.translatable("gui.jeecore.repair_tooltip",
                formatHearts(menu.selectedRepairHearts()))));
        if (round.token() != feedbackToken) {
            feedbackToken = round.token();
            playedFeedback = 0;
        }
        if (round.feedback() > 0 && round.feedback() != playedFeedback) {
            playedFeedback = round.feedback();
            if (round.feedback() == 3) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.25F));
            } else {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK,
                        round.feedback() == 2 ? 1.1F : 0.6F));
            }
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 31 && mouseX < leftPos + 49 && mouseY >= topPos + 99 && mouseY < topPos + 117) {
            graphics.renderTooltip(font, Component.translatable("gui.jeecore.power_core"), mouseX, mouseY);
        }
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var round = menu.round();
        if (!round.active() && button == 0 && bodyButton.isMouseOver(mouseX, mouseY)
                && minecraft.player != null && menu.stillValid(minecraft.player)
                && menu.selectedRepairHearts() <= 0) {
            noFuelUntil = Util.getMillis() + 1800;
            return true;
        }
        if (round.active() && button == 0 && bodyButton.isMouseOver(mouseX, mouseY)) {
            if (round.feedback() == 0 && attemptedToken != round.token() && minecraft.gameMode != null) {
                int x = (int) Math.floor(mouseX - bodyButton.getX());
                int y = (int) Math.floor(mouseY - bodyButton.getY());
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId,
                        TerragolemRepairRound.clickButton(round.token(), x, y));
                attemptedToken = round.token();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, imageWidth, imageHeight);
        boolean highlighted = repairGroupHighlighted(mouseX, mouseY);
        int x = leftPos + TerragolemRepairMenu.FIX_SLOT_X;
        int y = topPos + TerragolemRepairMenu.FIX_SLOT_Y;
        // Thick connector touches the slot frame and the body's left arm.
        graphics.fill(x + 17, y + 6, leftPos + 113, y + 11,
                highlighted ? 0xff929991 : 0xff626b61);
        // Reuse the first storage slot's exact 18x18 shading, without an extra frame.
        graphics.blit(BACKGROUND, x - 1, y - 1, 13, 29, 18, 18, imageWidth, imageHeight);
        if (highlighted) graphics.fill(x, y, x + 16, y + 16, 0x40ffffff);
        if (noFuelUntil > Util.getMillis() && (noFuelUntil - Util.getMillis()) / 150 % 2 == 0) {
            graphics.fill(x, y, x + 16, y + 16, 0xaaff5555);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.jeecore.fuel"), 14, 19, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.jeecore.power"), 14, 88, 0x606060, false);
        var round = menu.round();
        // During a round only the short feedback appears above the body, never instructions below
        // it.
        if (round.feedback() > 0 && (round.active() || menu.cooldown() > 0)) {
            int color = switch (round.feedback()) {
                case 1 -> 0xff4545;
                case 2 -> 0x7aef48;
                default -> 0x00ed65;
            };
            drawRepairText(graphics, Component.translatable("gui.jeecore.repair_feedback_" + round.feedback()),
                    138, 17, color);
        }
        if (!round.active()) {
            if (noFuelUntil > Util.getMillis()) {
                drawRepairText(graphics, Component.translatable("gui.jeecore.repair_no_fuel"), 138, 114, 0xffb4b4);
            } else if (round.resultPercent() > 0) {
                drawRepairText(graphics, Component.translatable("gui.jeecore.repair_quality", round.resultPercent()),
                        138, 114, 0xffffff);
                drawRepairText(graphics, Component.translatable("gui.jeecore.repair_healed", formatHearts(round.healedHearts())),
                        138, 125, 0xffffff);
            } else if (round.resultPercent() < 0) {
                drawRepairText(graphics, Component.translatable("gui.jeecore.repair_cancelled"), 138, 114, 0xffffff);
            }
        }
    }

    private void drawRepairText(GuiGraphics graphics, Component text, int centerX, int y, int color) {
        Component bold = text.copy().withStyle(ChatFormatting.BOLD);
        float scale = Math.min(0.65F, 92.0F / Math.max(1, font.width(bold)));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0);
        graphics.pose().scale(scale, scale, 1);
        int x = -font.width(bold) / 2;
        graphics.drawString(font, bold, x, 0, color, false);
        graphics.pose().popPose();
    }

    private static String formatHearts(float hearts) {
        return hearts == (int) hearts ? Integer.toString((int) hearts) : Float.toString(hearts);
    }

    private boolean repairGroupHighlighted(double mouseX, double mouseY) {
        return menu.cooldown() == 0 && bodyButton.isMouseOver(mouseX, mouseY);
    }

    private final class BodyButton extends AbstractButton {
        private BodyButton(int x, int y) {
            super(x, y, 52, 80, Component.translatable("gui.jeecore.repair"));
        }

        @Override public boolean isMouseOver(double x, double y) {
            if (!visible) return false;
            for (int[] part : BODY_PARTS) {
                if (x >= getX() + part[0] && x < getX() + part[2]
                        && y >= getY() + part[1] && y < getY() + part[3]) return true;
            }
            return false;
        }

        @Override protected boolean clicked(double x, double y) { return active && isMouseOver(x, y); }

        // Repair is activated only by a mouse click on the visible body silhouette.
        @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) { return false; }

        @Override public void onPress() {
            if (!active || menu.round().active() || minecraft.gameMode == null || minecraft.player == null) return;
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TerragolemRepairMenu.REPAIR_BUTTON);
            predictedCooldownUntil = minecraft.player.tickCount + TerragolemRepairManager.REPAIR_CLICK_COOLDOWN_TICKS;
            active = false;
        }

        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            isHovered = isMouseOver(mouseX, mouseY);
            float ratio = menu.maxHealth() > 0 ? Mth.clamp(menu.health() / menu.maxHealth(), 0.0F, 1.0F) : 0;
            float t = ratio < 0.5F ? ratio * 2 : (ratio - 0.5F) * 2;
            float r = ratio < 0.5F ? Mth.lerp(t, 0.55F, 1.0F) : Mth.lerp(t, 1.0F, 0.2F);
            float g = ratio < 0.5F ? Mth.lerp(t, 0.08F, 0.75F) : Mth.lerp(t, 0.75F, 0.85F);
            float b = ratio < 0.5F ? Mth.lerp(t, 0.08F, 0.1F) : Mth.lerp(t, 0.1F, 0.3F);
            boolean locked = !menu.round().active() && menu.cooldown() > 0;
            if (locked) r = g = b = 0.65F;
            float brightness = locked || active ? 1.0F : 0.55F;
            RenderSystem.enableBlend();
            float highlight = repairGroupHighlighted(mouseX, mouseY) ? 0.3F : 0.0F;
            graphics.setColor(Mth.lerp(highlight, r * brightness, 1.0F),
                    Mth.lerp(highlight, g * brightness, 1.0F),
                    Mth.lerp(highlight, b * brightness, 1.0F), 1.0F);
            // Draw the torso and limbs from the texture; the head is a featureless UI shape.
            graphics.blit(BODY, getX(), getY() + 18, 0, 18, width, height - 18, width, height);
            graphics.fill(getX() + 17, getY(), getX() + 35, getY() + 18, 0xff9b9b9b);
            graphics.fill(getX() + 18, getY() + 1, getX() + 34, getY() + 17, 0xffebebeb);
            graphics.fill(getX() + 19, getY() + 2, getX() + 33, getY() + 4, 0xffffffff);
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
            renderRepairTarget(graphics, partialTick);
        }

        private void renderRepairTarget(GuiGraphics graphics, float partialTick) {
            var round = menu.round();
            if (!round.active()) return;
            int cx = getX() + round.targetX(), cy = getY() + round.targetY();
            float elapsed = TerragolemRepairRound.TARGET_TICKS - round.remainingTicks() + partialTick;
            boolean perfect = Math.abs(elapsed - TerragolemRepairRound.PERFECT_TICK)
                    <= TerragolemRepairRound.PERFECT_TOLERANCE;
            int color = round.feedback() == 1 ? 0xffe36c62
                    : round.feedback() >= 2 || perfect ? 0xffe8ffc1 : 0xfff4f4f4;
            circle(graphics, cx, cy, 4, color);
            graphics.fill(cx - 1, cy - 1, cx + 2, cy + 2, color);
            if (round.feedback() == 0) {
                int radius = Math.round(Math.max(2, 4 + (TerragolemRepairRound.PERFECT_TICK - elapsed) * 0.4F));
                circle(graphics, cx, cy, radius, perfect ? 0xffc8ffa2 : 0xff7ddde2);
            } else if (round.feedback() >= 2) {
                int spread = 6 + (int) elapsed % 4;
                graphics.fill(cx - spread - 2, cy, cx - spread, cy + 1, color);
                graphics.fill(cx + spread, cy, cx + spread + 2, cy + 1, color);
                graphics.fill(cx, cy - spread - 2, cx + 1, cy - spread, color);
                graphics.fill(cx, cy + spread, cx + 1, cy + spread + 2, color);
            }
        }

        private void circle(GuiGraphics graphics, int cx, int cy, int radius, int color) {
            for (int i = 0; i < 48; i++) {
                double angle = i * Math.PI * 2 / 48;
                int x = cx + (int) Math.round(Math.cos(angle) * radius);
                int y = cy + (int) Math.round(Math.sin(angle) * radius);
                graphics.fill(x, y, x + 1, y + 1, color);
            }
        }

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
