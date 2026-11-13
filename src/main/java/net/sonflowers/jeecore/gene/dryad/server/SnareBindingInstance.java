package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.sonflowers.jeecore.gene.dryad.visual.RootSnareModel;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks one nonpersistent display assembly around a captured target; overlapping snares may share
 * ownership.
 */
final class SnareBindingInstance {
    private final List<Part> parts = new ArrayList<>(RootSnareModel.PART_COUNT);
    private final long startTick;
    private long endTick;
    private long lastTick = Long.MIN_VALUE;
    private long nextIdleTick;
    private final float width, height, yaw, phase;
    private final double groundY;
    private int frame;
    private boolean retracting;

    private SnareBindingInstance(ServerLevel level, LivingEntity target, int duration) {
        startTick = level.getGameTime();
        endTick = startTick + duration;
        nextIdleTick = startTick + RootSnareModel.GROW_TICKS + 6;
        width = target.getBbWidth();
        height = target.getBbHeight();
        yaw = target.getYRot() + (level.random.nextFloat() - 0.5f) * 40;
        phase = level.random.nextFloat() * (float) Math.PI * 2;
        // Capture before the gameplay code lowers the mob into the terrain.
        groundY = target.getY() + 0.01;
    }

    static SnareBindingInstance create(ServerLevel level, LivingEntity target, int duration) {
        SnareBindingInstance binding = new SnareBindingInstance(level, target, duration);
        for (int i = 0; i < RootSnareModel.PART_COUNT; i++) {
            Part part = i < 7 ? new BlockPart(level, blockState(i)) : new ItemPart(level);
            binding.parts.add(part);
            Display entity = part.entity();
            entity.setNoGravity(true);
            entity.setPos(target.getX(), binding.groundY, target.getZ());
            entity.setYRot(binding.yaw);
            binding.animatePart(i, 0, 1, 0, 0, 0);
            if (!level.addFreshEntity(entity)) {
                binding.discard();
                return null;
            }
        }
        return binding;
    }

    private static BlockState blockState(int part) {
        if (part < 4) return Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, part < 2);
        return Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, part == 6 ? 1 : 0);
    }

    void extend(long newEndTick) {
        if (newEndTick <= endTick) return;
        endTick = newEndTick;
        if (retracting) {
            retracting = false;
            frame = RootSnareModel.FRAME_COUNT - 1;
            animate(frame, 1, 0, 0, 6);
        }
    }

    void tick(LivingEntity target, long tick) {
        // Several overlapping snares may own the same visual assembly.
        if (lastTick == tick) return;
        lastTick = tick;
        for (Part part : parts) {
            Display display = part.entity();
            if (display.getX() != target.getX() || display.getZ() != target.getZ()) {
                display.setPos(target.getX(), groundY, target.getZ());
            }
        }
        if (tick >= endTick - RootSnareModel.WITHDRAW_LEAD_TICKS) {
            if (!retracting) {
                animate(RootSnareModel.FRAME_COUNT - 1, 0.02f, 0, 0, RootSnareModel.WITHDRAW_TICKS);
                retracting = true;
            }
            return;
        }
        int nextFrame = (int) Math.min(RootSnareModel.FRAME_COUNT - 1,
                (tick - startTick) / RootSnareModel.FRAME_TICKS);
        if (nextFrame != frame) {
            frame = nextFrame;
            animate(frame, 1, 0, 0, RootSnareModel.FRAME_TICKS);
        } else if (frame == RootSnareModel.FRAME_COUNT - 1 && tick >= nextIdleTick) {
            float tiltX = (float) Math.sin(tick * 0.04 + phase) * 0.012f;
            float tiltZ = (float) Math.sin(tick * 0.033 + phase + 1) * 0.01f;
            animate(frame, 1, tiltX, tiltZ, 20);
            nextIdleTick = tick + 20;
        }
    }

    private void animate(int pose, float growth, float tiltX, float tiltZ, int duration) {
        for (int i = 0; i < parts.size(); i++) animatePart(i, pose, growth, tiltX, tiltZ, duration);
    }

    private void animatePart(int part, int pose, float growth, float tiltX, float tiltZ, int duration) {
        Matrix4f matrix = RootSnareModel.fittedPose(pose, part, width, height, growth, tiltX, tiltZ);
        float cullingWidth = Math.max(2, RootSnareModel.horizontalScale(width) * 2.5f);
        parts.get(part).animate(matrix, duration, cullingWidth);
    }

    boolean isRemoved() {
        return parts.stream().anyMatch(part -> part.entity().isRemoved());
    }

    boolean intersects(ChunkPos chunk) {
        if (parts.isEmpty()) return false;
        Display origin = parts.getFirst().entity();
        double radius = RootSnareModel.horizontalScale(width) * 1.25;
        return origin.getX() + radius >= chunk.getMinBlockX() && origin.getX() - radius <= chunk.getMaxBlockX()
                && origin.getZ() + radius >= chunk.getMinBlockZ() && origin.getZ() - radius <= chunk.getMaxBlockZ();
    }

    void discard() {
        parts.forEach(part -> part.entity().discard());
    }

    private interface Part {
        Display entity();
        void animate(Matrix4f matrix, int duration, float width);
    }

    private static final class BlockPart extends Display.BlockDisplay implements Part {
        private final BlockState state;

        BlockPart(ServerLevel level, BlockState state) {
            super(EntityType.BLOCK_DISPLAY, level);
            this.state = state;
        }

        @Override
        public Display entity() { return this; }

        @Override
        public void animate(Matrix4f matrix, int duration, float width) {
            CompoundTag tag = SnareDisplayTransforms.animation(matrix, duration, width, 3);
            tag.put("block_state", NbtUtils.writeBlockState(state));
            super.readAdditionalSaveData(tag);
        }

        @Override
        public boolean shouldBeSaved() { return false; }
    }

    private static final class ItemPart extends Display.ItemDisplay implements Part {
        private final CompoundTag item;

        ItemPart(ServerLevel level) {
            super(EntityType.ITEM_DISPLAY, level);
            item = (CompoundTag) new ItemStack(Items.DEAD_BUSH).save(level.registryAccess());
        }

        @Override
        public Display entity() { return this; }

        @Override
        public void animate(Matrix4f matrix, int duration, float width) {
            CompoundTag tag = SnareDisplayTransforms.animation(matrix, duration, width, 3);
            tag.put("item", item);
            tag.putString("item_display", "none");
            super.readAdditionalSaveData(tag);
        }

        @Override
        public boolean shouldBeSaved() { return false; }
    }
}
