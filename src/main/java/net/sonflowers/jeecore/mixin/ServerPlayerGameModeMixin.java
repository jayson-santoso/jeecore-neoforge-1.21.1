package net.sonflowers.jeecore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemCoalMinerHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Observes successful ServerPlayerGameMode.removeBlock returns so Terragolem mining bonuses
 * require actual block removal.
 */
@Mixin(ServerPlayerGameMode.class)
public abstract class ServerPlayerGameModeMixin {
    @Shadow @Final protected ServerPlayer player;

    // NeoForge adds removeBlock; it has no vanilla obfuscation mapping.
    @Inject(method = "removeBlock", at = @At("RETURN"), remap = false)
    private void jeecore$onBlockRemoved(BlockPos pos, BlockState state, boolean canHarvest,
                                       CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            TerragolemCoalMinerHandler.onBlockRemoved(player, pos, state);
        }
    }
}
