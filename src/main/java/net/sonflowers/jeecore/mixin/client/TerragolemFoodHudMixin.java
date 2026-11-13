package net.sonflowers.jeecore.mixin.client;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Changes client hunger sprite selection while preserving vanilla HUD layout, animation, and
 * visibility.
 */
@Mixin(Gui.class)
public abstract class TerragolemFoodHudMixin {
    @Unique
    private static final Map<ResourceLocation, ResourceLocation> jeecore$foodSprites = Stream.of(
            "food_empty", "food_half", "food_full",
            "food_empty_hunger", "food_half_hunger", "food_full_hunger")
            .collect(Collectors.toUnmodifiableMap(
                    name -> ResourceLocation.withDefaultNamespace("hud/" + name),
                    name -> ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "hud/terragolem/" + name)));

    // Exactly three draw calls in 1.21.1: empty background, full icon, and half icon.
    @Redirect(method = "renderFood(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/player/Player;II)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V"),
            require = 3, expect = 3, allow = 3)
    private void jeecore$drawFuelIcon(GuiGraphics graphics, ResourceLocation sprite,
                                    int x, int y, int width, int height,
                                    GuiGraphics originalGraphics, Player player, int foodY, int foodX) {
        ResourceLocation selected = GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM
                ? jeecore$foodSprites.getOrDefault(sprite, sprite) : sprite;
        graphics.blitSprite(selected, x, y, width, height);
    }
}
