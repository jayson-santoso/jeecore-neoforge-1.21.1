package net.sonflowers.jeecore.core.gene;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.sonflowers.jeecore.JeeCore;

import java.util.function.Supplier;

/**
 * Registers player attachments for persistent gene and fuel data and transient Windrunner state.
 */
public class GeneDataAttachment {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, JeeCore.MOD_ID);

    public static final Supplier<AttachmentType<net.sonflowers.jeecore.gene.nimbus.WindrunnerState>> WINDRUNNER =
            ATTACHMENT_TYPES.register("windrunner", () -> AttachmentType.builder(
                    net.sonflowers.jeecore.gene.nimbus.WindrunnerState::new).build());

    public static final Supplier<AttachmentType<PlayerGeneData>> PLAYER_GENE_DATA =
            ATTACHMENT_TYPES.register("player_gene_data", () ->
                    AttachmentType.serializable(PlayerGeneData::new).build());

    public static final Supplier<AttachmentType<net.sonflowers.jeecore.gene.terragolem.data.TerragolemFuelData>> TERRAGOLEM_FUEL_DATA =
            ATTACHMENT_TYPES.register("terragolem_fuel_data", () -> AttachmentType.serializable(
                    net.sonflowers.jeecore.gene.terragolem.data.TerragolemFuelData::new).build());
}
