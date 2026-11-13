package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Implements a temporary server plant display; vanilla tracking carries its animation to clients.
 */
final class SnareVegetationDisplay extends Display.BlockDisplay {
    private final BlockState plant;
    private final float scale;
    private final int halfOffset;
    private final float windDirection;

    SnareVegetationDisplay(ServerLevel level, BlockState plant, float scale, int halfOffset, float windDirection) {
        super(EntityType.BLOCK_DISPLAY, level);
        this.plant = plant;
        this.scale = scale;
        this.halfOffset = halfOffset;
        this.windDirection = windDirection;
        setNoGravity(true);
        animate(0.02f, 0, 0);
    }

    void animate(float growth, float tilt, int duration) {
        // The shared foot pivot keeps both halves joined through growth and sway.
        CompoundTag tag = SnareDisplayTransforms.animation(
                SnareDisplayTransforms.plant(scale, halfOffset, growth, tilt, windDirection), duration, 2, 3);
        tag.put("block_state", NbtUtils.writeBlockState(plant));
        // Display setters are private; its protected NBT reader updates synced transforms without
        // reloading entity position, UUID, or other state.
        super.readAdditionalSaveData(tag);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

}
