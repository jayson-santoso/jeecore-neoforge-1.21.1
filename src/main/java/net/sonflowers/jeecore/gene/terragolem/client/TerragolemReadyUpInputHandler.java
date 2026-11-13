package net.sonflowers.jeecore.gene.terragolem.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.sonflowers.jeecore.core.registry.ModItems;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.nimbus.client.WindRenderer;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemReadyUpClickPayload;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemJumpPayload;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.neoforged.neoforge.event.entity.living.LivingEvent;

import java.util.UUID;

/**
 * Routes local charge and beam input to the server, tracks jumps, and forwards held fuel use.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID, value = Dist.CLIENT)
public class TerragolemReadyUpInputHandler {
    @SubscribeEvent
    public static void onActualPlayerJump(LivingEvent.LivingJumpEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && event.getEntity() == player && player.onGround()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM) {
            PacketDistributor.sendToServer(new TerragolemJumpPayload());
        }
    }

    @SubscribeEvent
    public static void onChargeMovementInput(MovementInputUpdateEvent event) {
        if (!TerragolemReadyUpRenderer.isReadyUp(event.getEntity().getUUID())) return;
        event.getEntity().setSprinting(false);
        // Preserve directional and jump inputs; the charge attribute allows a 5% shuffle.
    }

    @SubscribeEvent
    public static void onLevelUnload(net.neoforged.neoforge.event.level.LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) TerragolemReadyUpRenderer.resetAll();
    }

    @SubscribeEvent
    public static void onPlayerTick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player) return;
        if (!event.getEntity().isAlive()) {
            TerragolemReadyUpRenderer.reset(event.getEntity().getUUID());
            return;
        }
        if (TerragolemReadyUpRenderer.isReadyUp(mc.player.getUUID())) {
            var motion = mc.player.getDeltaMovement();
            mc.player.setDeltaMovement(TerragolemReadyUpManager.limitChargeMotion(motion));
            mc.player.setSprinting(false);
        }
        // Beam activation consumes the mouse event, so vanilla never sees a held use key. Route
        // powered coal through normal item use while the physical button stays down. The server
        // remains responsible for nutrition, cooling, consumption and cooldowns.
        if (mc.screen != null || mc.gameMode == null || mc.player.isSpectator()
                || mc.player.isUsingItem() || mc.player.tickCount % 4 != 0
                || !TerragolemReadyUpRenderer.isHeld(mc.player.getUUID())
                || !mc.player.getFoodData().needsFood()
                || GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                    != GLFW.GLFW_PRESS) return;
        for (InteractionHand hand : InteractionHand.values()) {
            var stack = mc.player.getItemInHand(hand);
            if (stack.is(ModItems.POWERED_COAL.get())
                    && !mc.player.getCooldowns().isOnCooldown(stack.getItem())) {
                mc.gameMode.useItem(mc.player, hand);
                break;
            }
        }
    }
    @SubscribeEvent
    public static void onMouseButton(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive() || mc.screen != null) return;
        UUID uuid = mc.player.getUUID();
        boolean readyUp = TerragolemReadyUpRenderer.isReadyUp(uuid);
        boolean held = TerragolemReadyUpRenderer.isHeld(uuid);
        if (!readyUp && !held) return;

        int button = event.getButton();
        boolean down = event.getAction() == GLFW.GLFW_PRESS;

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Left clicks only matter during the ready-up window.
            if (!readyUp) return;
            // Post-release: slowness (server) and low FOV (local) for 1.5s. Left-button release
            // requires at least 1 fully charged segment.
            int leftSegments = getFullyChargedSegments(mc);
            if (down && leftSegments >= 1) {
                TerragolemReadyUpRenderer.startAftermathTicks(30, TerragolemReadyUpRenderer.getCharge(uuid));
                // Screen shake scaled by charged segments: 1-2 low, 3-4 medium, 5-6 high.
                float shake = leftSegments <= 2 ? 0.25f : leftSegments <= 4 ? 0.45f : 0.7f;
                WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, shake);
            }
            PacketDistributor.sendToServer(new TerragolemReadyUpClickPayload(button, down));
            event.setCanceled(true);
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            // right-button press only during ready-up, right-button release only while the flare is
            // held.
            if (down && !readyUp) return;
            if (!down && !held) return;
            PacketDistributor.sendToServer(new TerragolemReadyUpClickPayload(button, down));
            // right-button release: 2 second stun — server applies slowness, local FOV stays low.
            if (!down && held) {
                TerragolemReadyUpRenderer.startAftermathTicks(40, TerragolemReadyUpRenderer.getRenderCharge(uuid));
            }
            // right-button press: white screen flash and screen shake when the hold actually
            // registers (>= 3 segments).
            if (down && readyUp && getFullyChargedSegments(mc) >= 3) {
                TerragolemReadyUpRenderer.whiteFlashIntensity = 1.0f;
                WindRenderer.screenShakeIntensity = Math.max(WindRenderer.screenShakeIntensity, 0.35f);
            }
            // Consume right releases while holding the flare, and right clicks the server will act
            // on.
            if ((held && !down) || getFullyChargedSegments(mc) >= 3) {
                event.setCanceled(true);
            }
        }
    }

    private static int getFullyChargedSegments(Minecraft mc) {
        float elapsed = TerragolemReadyUpRenderer.getElapsed(mc.player.getUUID());
        if (elapsed < 0) return 0;
        return (int) Math.min(6, elapsed / (TerragolemReadyUpRenderer.CHARGE_TICKS / 6));
    }
}
