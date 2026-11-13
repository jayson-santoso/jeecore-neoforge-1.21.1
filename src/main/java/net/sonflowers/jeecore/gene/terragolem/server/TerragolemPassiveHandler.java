package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies Terragolem attributes and movement restrictions; shared pose handling keeps client
 * swimming visuals consistent.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class TerragolemPassiveHandler {
    public static final float INCOMING_DAMAGE_MULTIPLIER = 0.5f;

    private static final double ATTACK_DAMAGE_BONUS = 0.30;
    private static final double ATTACK_SPEED_PENALTY = -0.40;
    private static final double BASE_MOVEMENT_PENALTY = -0.25;
    private static final double MAX_MOVEMENT_PENALTY = -0.50;
    private static final double MOVEMENT_UPDATE_EPSILON = 0.0025;
    private static final double MAX_SINK_SPEED = -0.18;
    private static final double SINK_ACCELERATION = 0.035;

    private static final ResourceLocation ATTACK_DAMAGE_ID = id("terragolem_attack_damage");
    private static final ResourceLocation ATTACK_SPEED_ID = id("terragolem_attack_speed");
    private static final ResourceLocation MOVEMENT_SPEED_ID = id("terragolem_movement_speed");
    private static final ResourceLocation AIR_SPEED_ID = id("terragolem_air_speed");

    private static final Map<UUID, Double> APPLIED_MOVEMENT_PENALTIES = new ConcurrentHashMap<>();

    private TerragolemPassiveHandler() {}

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM) {
            removePassiveAttributes(player);
            return;
        }

        applyPassiveAttributes(player);
        player.setAirSupply(player.getMaxAirSupply());
    }

    public static void applyPassiveAttributes(Player player) {
        addModifierIfMissing(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_ID,
                ATTACK_DAMAGE_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addModifierIfMissing(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID,
                ATTACK_SPEED_PENALTY, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        updateMovementAttributes(player);
    }

    public static void removePassiveAttributes(Player player) {
        removeModifier(player.getAttribute(Attributes.ATTACK_DAMAGE), ATTACK_DAMAGE_ID);
        removeModifier(player.getAttribute(Attributes.ATTACK_SPEED), ATTACK_SPEED_ID);
        removeModifier(player.getAttribute(Attributes.MOVEMENT_SPEED), MOVEMENT_SPEED_ID);
        removeModifier(player.getAttribute(Attributes.FLYING_SPEED), AIR_SPEED_ID);
        APPLIED_MOVEMENT_PENALTIES.remove(player.getUUID());
    }

    private static void updateMovementAttributes(Player player) {
        float heatRatio = Math.min(1.0f,
                TerragolemOverheatManager.getHeat(player.getUUID()) / TerragolemOverheatManager.MAX_OVERHEAT);
        double penalty = BASE_MOVEMENT_PENALTY
                + (MAX_MOVEMENT_PENALTY - BASE_MOVEMENT_PENALTY) * heatRatio;
        Double previous = APPLIED_MOVEMENT_PENALTIES.get(player.getUUID());
        AttributeInstance movementSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance airSpeed = player.getAttribute(Attributes.FLYING_SPEED);
        boolean modifiersPresent = movementSpeed != null && movementSpeed.hasModifier(MOVEMENT_SPEED_ID)
                && (airSpeed == null || airSpeed.hasModifier(AIR_SPEED_ID));
        if (modifiersPresent && previous != null && Math.abs(previous - penalty) < MOVEMENT_UPDATE_EPSILON) return;

        replaceModifier(movementSpeed, MOVEMENT_SPEED_ID, penalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        replaceModifier(airSpeed, AIR_SPEED_ID, penalty,
                AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        APPLIED_MOVEMENT_PENALTIES.put(player.getUUID(), penalty);
    }

    @SubscribeEvent
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM) return;

        // Clear the swimming pose on both sides so the local player cannot enter crawl-swimming
        // visuals while the server enforces the same state.
        player.setSwimming(false);
        if (player.level().isClientSide) return;

        player.setAirSupply(player.getMaxAirSupply());
        if (player.isUnderWater()) {
            applyWaterWeight(player);
        } else {
            limitAirMovement(player);
        }
    }

    private static void applyWaterWeight(Player player) {
        player.setSwimming(false);
        Vec3 movement = player.getDeltaMovement();

        // Preserve all horizontal water movement and normal upward jumps. Weight only pulls the
        // player down when they are no longer moving upward.
        double vertical = movement.y;
        if (!player.onGround() && vertical <= 0.0) {
            vertical = Math.max(MAX_SINK_SPEED, vertical - SINK_ACCELERATION);
        }
        player.setDeltaMovement(movement.x, vertical, movement.z);
        player.hurtMarked = true;
    }

    private static void limitAirMovement(Player player) {
        if (player.onGround() || player.isFallFlying() || player.getAbilities().flying || player.isPassenger()) return;
        if (Math.abs(player.xxa) < 0.001f && Math.abs(player.zza) < 0.001f) return;

        Vec3 movement = player.getDeltaMovement();
        double horizontalSqr = movement.x * movement.x + movement.z * movement.z;
        double limit = player.getAttributeValue(Attributes.MOVEMENT_SPEED) * (player.isSprinting() ? 1.3 : 1.0);
        if (horizontalSqr <= limit * limit || horizontalSqr <= 0.0) return;

        double scale = limit / Math.sqrt(horizontalSqr);
        player.setDeltaMovement(movement.x * scale, movement.y, movement.z * scale);
        player.hurtMarked = true;
    }

    @SubscribeEvent
    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (TerragolemFuelManager.isFuel(event.getItem())) return;
        if (GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM
                && event.getItem().has(DataComponents.FOOD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (GeneHelper.getPlayerGene(event.getEntity()) == Gene.TERRAGOLEM
                && TerragolemFuelManager.isFuel(event.getItemStack())) {
            event.setCancellationResult(TerragolemFuelManager.consume(event.getEntity(), event.getHand()));
            event.setCanceled(true);
            return;
        }
        if (GeneHelper.getPlayerGene(event.getEntity()) == Gene.TERRAGOLEM
                && event.getItemStack().has(DataComponents.FOOD)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (GeneHelper.getPlayerGene(event.getEntity()) != Gene.TERRAGOLEM) return;
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof CakeBlock || block instanceof CandleCakeBlock) {
            event.setCanceled(true);
        }
    }

    private static void addModifierIfMissing(AttributeInstance attribute, ResourceLocation id,
                                             double amount, AttributeModifier.Operation operation) {
        if (attribute != null && !attribute.hasModifier(id)) {
            attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void replaceModifier(AttributeInstance attribute, ResourceLocation id,
                                        double amount, AttributeModifier.Operation operation) {
        if (attribute == null) return;
        attribute.removeModifier(id);
        attribute.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    private static void removeModifier(AttributeInstance attribute, ResourceLocation id) {
        if (attribute != null) attribute.removeModifier(id);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, path);
    }
}
