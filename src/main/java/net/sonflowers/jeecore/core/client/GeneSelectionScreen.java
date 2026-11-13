package net.sonflowers.jeecore.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.network.GeneSelectionPayload;

/**
 * Displays gene choices on the client and sends the selected gene to the server.
 */
public class GeneSelectionScreen extends Screen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;

    public GeneSelectionScreen() {
        super(Component.literal("Choose Your Gene"));
    }

    public static void open() {
        Minecraft.getInstance().setScreen(new GeneSelectionScreen());
    }

    @Override
    protected void init() {
        super.init();

        int startY = this.height / 2 - (BUTTON_SPACING * 2);

        addRenderableWidget(Button.builder(
                Component.literal("Nimbus").withColor(Gene.NIMBUS.getColor()),
                btn -> selectGene(Gene.NIMBUS))
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, startY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Infernal").withColor(Gene.INFERNAL.getColor()),
                btn -> selectGene(Gene.INFERNAL))
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + BUTTON_SPACING, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Dryad").withColor(Gene.DRYAD.getColor()),
                btn -> selectGene(Gene.DRYAD))
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 2, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Terragolem").withColor(Gene.TERRAGOLEM.getColor()),
                btn -> selectGene(Gene.TERRAGOLEM))
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 3, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());

        addRenderableWidget(Button.builder(
                Component.literal("Flicker").withColor(Gene.FLICKER.getColor()),
                btn -> selectGene(Gene.FLICKER))
                .bounds(this.width / 2 - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 4, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void selectGene(Gene gene) {
        PacketDistributor.sendToServer(new GeneSelectionPayload(gene.getSerializedName()));
        this.onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
