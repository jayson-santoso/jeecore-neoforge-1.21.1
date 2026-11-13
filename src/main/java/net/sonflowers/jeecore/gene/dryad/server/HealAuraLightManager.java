package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModBlocks;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages server aura light ownership and cleanup without force-loading chunks.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class HealAuraLightManager {
    private record Key(ResourceKey<Level> dimension, BlockPos position) {}
    private static final class Lease {
        final BlockState original;
        int users = 1;
        Lease(BlockState original) { this.original = original; }
    }
    private static final Map<Key, Lease> LIGHTS = new HashMap<>();

    private HealAuraLightManager() {}

    @Nullable
    public static BlockPos acquire(ServerLevel level, BlockPos center) {
        for (int height = 0; height <= 2; height++) {
            BlockPos pos = center.above(height).immutable();
            if (level.isOutsideBuildHeight(pos) || !level.hasChunkAt(pos)) continue;
            BlockState state = level.getBlockState(pos);
            Key key = new Key(level.dimension(), pos);
            if (state.is(ModBlocks.HEAL_AURA_LIGHT.get())) {
                Lease lease = LIGHTS.get(key);
                if (lease == null) LIGHTS.put(key, new Lease(Blocks.AIR.defaultBlockState()));
                else lease.users++;
                level.scheduleTick(pos, ModBlocks.HEAL_AURA_LIGHT.get(), 20);
                return pos;
            }
            // Never replace solid blocks, plants, fluids, or another light source.
            if (!state.isAir()) continue;
            if (!level.setBlock(pos, ModBlocks.HEAL_AURA_LIGHT.get().defaultBlockState(), 3)) continue;
            LIGHTS.put(key, new Lease(state));
            return pos;
        }
        return null;
    }

    public static boolean isActive(ServerLevel level, BlockPos pos) {
        return LIGHTS.containsKey(new Key(level.dimension(), pos));
    }

    public static void release(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) return;
        Key key = new Key(level.dimension(), pos);
        Lease lease = LIGHTS.get(key);
        if (lease == null || --lease.users > 0) return;
        LIGHTS.remove(key);
        restore(level, pos, lease.original);
    }

    private static void restore(ServerLevel level, BlockPos pos, BlockState original) {
        // An unloaded light retains its scheduled cleanup tick. Do not force-load it.
        if (level.hasChunkAt(pos) && level.getBlockState(pos).is(ModBlocks.HEAL_AURA_LIGHT.get())) {
            level.setBlock(pos, original, 3);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LIGHTS.forEach((key, lease) -> {
            ServerLevel level = event.getServer().getLevel(key.dimension);
            if (level != null) restore(level, key.position, lease.original);
        });
        LIGHTS.clear();
    }
}
