package net.sonflowers.jeecore.gene.dryad.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.sonflowers.jeecore.gene.dryad.server.HealAuraLightManager;

/**
 * Provides invisible, temporary light for Dryad healing auras without an inventory item.
 */
public final class HealAuraLightBlock extends Block {
    public static final MapCodec<HealAuraLightBlock> CODEC = simpleCodec(HealAuraLightBlock::new);

    public HealAuraLightBlock(Properties properties) { super(properties); }

    @Override
    public MapCodec<HealAuraLightBlock> codec() { return CODEC; }

    @Override
    protected RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState previous, boolean moving) {
        if (!level.isClientSide) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Scheduled block ticks survive saves/unloads. After a restart there are no active leases,
        // so an orphaned light removes itself when loaded.
        if (!HealAuraLightManager.isActive(level, pos)) level.removeBlock(pos, false);
        else level.scheduleTick(pos, this, 20);
    }
}
