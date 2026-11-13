package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.nimbus.network.GlideStatePayload;
import com.illusivesoulworks.caelus.api.CaelusApi;

/**
 * Runs server glide toggles and resource ticks, updating the Caelus flight attribute and client
 * mirror.
 */
public class NimbusGlideManager {
    private static final ResourceLocation GLIDE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "nimbus_glide");

    public static void toggleGlide(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.NIMBUS) return;

        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean newState = !data.isGlideToggled();
        data.setGlideToggled(newState);

        // Update Caelus flight attribute.
        updateFlightAttribute(player, newState);

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                newState ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
                SoundSource.AMBIENT, 1.0f, 2.0f);

        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new GlideStatePayload(newState, data.getGlideResource()));
        }
    }

    /**
     * Enables the Caelus flight attribute only while glide is toggled and resource remains.
     */
    private static void updateFlightAttribute(Player player, boolean enabled) {
        AttributeInstance flightAttr = player.getAttribute(CaelusApi.getInstance().getFallFlyingAttribute());
        if (flightAttr == null) return;

        flightAttr.removeModifier(GLIDE_MODIFIER_ID);

        // Add modifier if enabled and has resource.
        if (enabled && getGlideResource(player) > 0) {
            flightAttr.addPermanentModifier(new AttributeModifier(
                GLIDE_MODIFIER_ID,
                1.0, // Value >= 1 enables flight.
                AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    public static boolean isGlideToggled(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.isGlideToggled();
    }

    public static int getGlideResource(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        return data.getGlideResource();
    }

    public static void setGlideResource(Player player, int amount) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        data.setGlideResource(amount);
        int resource = data.getGlideResource();

        // Update flight attribute based on resource.
        if (data.isGlideToggled()) {
            updateFlightAttribute(player, resource > 0);
        }

        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new GlideStatePayload(data.isGlideToggled(), resource));
        }
    }

    public static void addGlideResource(Player player, int amount) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int oldResource = data.getGlideResource();
        data.addGlideResource(amount);
        int newResource = data.getGlideResource();
        if (oldResource == newResource) return;

        // Update flight attribute if resource changed from 0 or to 0.
        if (data.isGlideToggled() && ((oldResource == 0 && newResource > 0) || (oldResource > 0 && newResource == 0))) {
            updateFlightAttribute(player, newResource > 0);
        }

        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new GlideStatePayload(data.isGlideToggled(), newResource));
        }
    }

    /**
     * Recharges idle glide resource and spends it while gliding, accounting for rain and nearby
     * water.
     */
    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.NIMBUS) return;

        // Recharge glide resource.
        tickRecharge(player);

        // Handle gliding resource depletion.
        if (isGlideToggled(player) && player.isFallFlying()) {
            tickGliding(player);
        }

        if (isGlideToggled(player) && player.isInWater() && player.isFallFlying()) {
            player.stopFallFlying();
        }
    }

    private static void tickRecharge(Player player) {
        Level level = player.level();
        BlockPos pos = player.blockPosition();

        boolean inRain = level.isRainingAt(pos.above());
        if (inRain) {
            addGlideResource(player, 1);
            return;
        }

        // Check if in water (need at least 4 water source blocks nearby).
        if (player.isInWater()) {
            int waterBlocks = 0;
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        BlockPos checkPos = pos.offset(x, y, z);
                        FluidState fluidState = level.getFluidState(checkPos);
                        if (fluidState.getType() == Fluids.WATER && fluidState.isSource()) {
                            waterBlocks++;
                        }
                    }
                }
            }

            if (waterBlocks >= 4) {
                addGlideResource(player, 2);
            }
        }
    }

    private static void tickGliding(Player player) {
        addGlideResource(player, -1);

        // Stop gliding if out of resource (Caelus will handle this automatically).
        if (getGlideResource(player) <= 0) {
            player.stopFallFlying();
        }
    }

    /**
     * Initializes the flight modifier in its disabled state when Nimbus is selected.
     */
    public static void applyGlideAttribute(Player player) {
        // Initialize with attribute disabled (will be enabled when toggled on).
        updateFlightAttribute(player, false);
    }

    /**
     * Removes the Caelus modifier when Nimbus is deselected.
     */
    public static void removeGlideAttribute(Player player) {
        AttributeInstance flightAttr = player.getAttribute(CaelusApi.getInstance().getFallFlyingAttribute());
        if (flightAttr != null) {
            flightAttr.removeModifier(GLIDE_MODIFIER_ID);
        }
    }
}
