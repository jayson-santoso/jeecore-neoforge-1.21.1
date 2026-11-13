package net.sonflowers.jeecore.core.registry;

import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.block.HealAuraLightBlock;

/**
 * Declares deferred block registrations, including the temporary Dryad aura light.
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(JeeCore.MOD_ID);
    // No item: this invisible, replaceable block only exists during an active heal aura.
    public static final DeferredBlock<HealAuraLightBlock> HEAL_AURA_LIGHT = BLOCKS.registerBlock(
            "heal_aura_light", HealAuraLightBlock::new,
            BlockBehaviour.Properties.of().noCollission().noOcclusion().replaceable().noLootTable()
                    .lightLevel(state -> 14));

    private ModBlocks() {}
}
