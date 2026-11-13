package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.LightLayer;

/**
 * Implements a temporary server block display for impact ripples without replacing supporting
 * terrain.
 */
public final class ImpactRippleDisplay extends Display.BlockDisplay {
    private final BlockPos ground;
    private final BlockState original;
    private final long started;
    private final int blockLight, skyLight;

    public ImpactRippleDisplay(ServerLevel level, BlockPos ground, BlockState original) {
        super(EntityType.BLOCK_DISPLAY, level);
        this.ground = ground.immutable();
        this.original = original;
        this.started = level.getGameTime();
        // Entity origin is inside the original opaque block; sample the exposed surface instead.
        this.blockLight = Math.max(4, level.getBrightness(LightLayer.BLOCK, ground.above()));
        this.skyLight = level.getBrightness(LightLayer.SKY, ground.above());
        setPos(ground.getX(), ground.getY(), ground.getZ());
        setNoGravity(true);
        addTag("jeecore_impact_ripple");
        animate(-0.002f, 0);
    }

    private void animate(float height, int duration) {
        CompoundTag transformation = new CompoundTag();
        transformation.put("translation", floats(0, height, 0));
        transformation.put("scale", floats(1, 1, 1));
        transformation.put("left_rotation", floats(0, 0, 0, 1));
        transformation.put("right_rotation", floats(0, 0, 0, 1));
        CompoundTag tag = new CompoundTag();
        tag.put("transformation", transformation);
        tag.put("block_state", NbtUtils.writeBlockState(original));
        tag.putInt("interpolation_duration", duration);
        tag.putInt("start_interpolation", 0);
        tag.putFloat("width", 1);
        tag.putFloat("height", 1.5f);
        tag.putFloat("shadow_radius", 0);
        CompoundTag brightness = new CompoundTag();
        brightness.putInt("block", blockLight);
        brightness.putInt("sky", skyLight);
        tag.put("brightness", brightness);
        // Display's setters are private; this updates vanilla synced transformation data.
        super.readAdditionalSaveData(tag);
    }

    @Override public void tick() {
        super.tick();
        long age = level().getGameTime() - started;
        if (!level().getBlockState(ground).equals(original) || age >= 10) { discard(); return; }
        if (age == 2) animate(0.28f, 2);
        if (age == 5) animate(-0.002f, 3);
    }

    @Override public boolean shouldBeSaved() { return false; }

    private static ListTag floats(float... values) {
        ListTag tag = new ListTag();
        for (float value : values) tag.add(FloatTag.valueOf(value));
        return tag;
    }
}
