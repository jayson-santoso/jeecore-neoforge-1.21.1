package net.sonflowers.jeecore.gene.dryad.server;

import net.sonflowers.jeecore.core.ability.AbilityHandler;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.dryad.network.RootSnareVfxPayload;
import net.sonflowers.jeecore.gene.dryad.visual.RootSnareModel;

import java.util.*;

/**
 * Owns server snare fields, temporary terrain, captured entities, and restoration on expiry or
 * unload.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class DryadRootSnareManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_RADIUS = 4;
    private static final int STUN_RADIUS = 5;
    private static final int DURATION_TICKS = 160; // 8 seconds.
    private static final int VEGETATION_WITHDRAW_TICKS = 4;
    private static final int VEGETATION_WITHDRAW_RING_DELAY = 4;
    private static final int GROWTH_DELAY_TICKS = 3; // Grows 1 block every 3 ticks.
    private static final int VEGETATION_TRACKING_LEAD = 2;
    private static final int TEMPORARY_BLOCK_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
    private static final List<BlockOffset>[] RING_OFFSETS = createRingOffsets();

    private static final Map<UUID, RootSnareInstance> ACTIVE_SNARES = new HashMap<>();

    private record BlockOffset(int x, int z) {
    }

    private static class RootSnareInstance {
        final BlockPos center;
        final ServerLevel level;
        final Map<BlockPos, BlockState> originalBlocks = new HashMap<>();
        final Map<BlockPos, VegetationInstance> vegetation = new HashMap<>();
        final SnareVegetationLayout vegetationLayout;
        final Map<UUID, SnareBindingInstance> bindings = new HashMap<>();
        final Map<BlockPos, BlockState> removedCover = new HashMap<>();
        final Map<UUID, SnaredEntityState> affectedEntities = new HashMap<>();
        int currentRadius = 0;
        int ticksSinceLastGrowth = 0;
        int totalTicks = 0;

        RootSnareInstance(BlockPos center, ServerLevel level) {
            this.center = center;
            this.level = level;
            this.vegetationLayout = new SnareVegetationLayout(level.random.nextLong(), MAX_RADIUS + 0.5f);
        }
    }

    public static void activate(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;

        UUID playerId = player.getUUID();

        cleanup(playerId);

        BlockPos playerPos = player.blockPosition();
        BlockPos groundPos = findGroundBelow(serverLevel, playerPos);

        if (groundPos == null) return;

        RootSnareInstance snare = new RootSnareInstance(groundPos, serverLevel);
        ACTIVE_SNARES.put(playerId, snare);
        // Track the next ring while its plants are hidden, then reveal it with its ground.
        prepareVegetationRing(snare, 0);
        prepareVegetationRing(snare, 1);
        growRadius(snare, 0);

        AbilityHandler.setSecondaryCooldown(player);

        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);

        serverLevel.playSound(null, groundPos, SoundEvents.ROOTED_DIRT_PLACE, 
                SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    public static void tick(ServerLevel level) {
        if (ACTIVE_SNARES.isEmpty()) return;

        Iterator<Map.Entry<UUID, RootSnareInstance>> iterator = ACTIVE_SNARES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<UUID, RootSnareInstance> entry = iterator.next();
            RootSnareInstance snare = entry.getValue();

            // Only tick snares in their own dimension.
            if (snare.level != level) continue;

            snare.totalTicks++;
            // Recover direct entity-data edits that bypass Mob.setNoAi.
            for (Map.Entry<UUID, SnaredEntityState> affected : snare.affectedEntities.entrySet()) {
                if (snare.totalTicks < DURATION_TICKS
                        && level.getEntity(affected.getKey()) instanceof Mob mob && !mob.isNoAi()) {
                    maintainAiLock(mob, affected.getValue());
                }
            }

            if (snare.totalTicks >= DURATION_TICKS) {
                restoreBlocks(snare);
                iterator.remove();
                continue;
            }

            // Grow outward if not at max radius.
            if (snare.currentRadius < MAX_RADIUS) {
                snare.ticksSinceLastGrowth++;

                if (snare.ticksSinceLastGrowth >= GROWTH_DELAY_TICKS) {
                    snare.currentRadius++;
                    growRadius(snare, snare.currentRadius);
                    snare.ticksSinceLastGrowth = 0;
                    if (snare.currentRadius < MAX_RADIUS) {
                        prepareVegetationRing(snare, snare.currentRadius + 1);
                    }
                }
            }
            // Transform the ground before starting the matching plant animations.
            tickVegetation(snare);
            tickBindings(snare);
        }
    }

    private static void prepareVegetationRing(RootSnareInstance snare, int radius) {
        int revealTick = radius * GROWTH_DELAY_TICKS;
        for (BlockOffset offset : getOffsetsAtRadius(radius)) {
            SnareVegetationLayout.Plant layout = snare.vegetationLayout.at(offset.x(), offset.z());
            if (layout == null) continue;
            BlockPos ground = findGroundAtXZ(snare.level, snare.center.getX() + offset.x(),
                    snare.center.getZ() + offset.z(), snare.center.getY());
            if (ground == null) continue;
            BlockState plant = switch (layout.species()) {
                case TALL_GRASS -> Blocks.TALL_GRASS.defaultBlockState();
                case FERN -> Blocks.FERN.defaultBlockState();
                case SHORT_GRASS -> Blocks.SHORT_GRASS.defaultBlockState();
                case TULIP -> Blocks.WHITE_TULIP.defaultBlockState();
                case DAISY -> Blocks.OXEYE_DAISY.defaultBlockState();
                case DANDELION -> Blocks.DANDELION.defaultBlockState();
                case ROSE_BUSH -> Blocks.ROSE_BUSH.defaultBlockState();
                case PEONY -> Blocks.PEONY.defaultBlockState();
                case LILAC -> Blocks.LILAC.defaultBlockState();
            };
            spawnVegetation(snare, ground.above(), plant, layout, revealTick);
        }
    }

    private static void growRadius(RootSnareInstance snare, int radius) {
        for (BlockOffset offset : getOffsetsAtRadius(radius)) {
            BlockPos pos = snare.center.offset(offset.x(), 0, offset.z());
            // Find ground by raycasting down from above in the snare's dimension.
            BlockPos groundPos = findGroundAtXZ(snare.level, pos.getX(), pos.getZ(), snare.center.getY());

            if (groundPos == null) continue;

            BlockState currentState = snare.level.getBlockState(groundPos);

            // Only convert solid ground blocks (skip air).
            if (!currentState.isAir() && currentState.isSolidRender(snare.level, groundPos)) {
                // Store original block if not already stored.
                if (!snare.originalBlocks.containsKey(groundPos)) {
                    snare.originalBlocks.put(groundPos, currentState);

                    // Choose block type: 70% moss, 30% rooted dirt.
                    BlockState newBlock = snare.level.random.nextFloat() < 0.7f ? 
                            Blocks.MOSS_BLOCK.defaultBlockState() : 
                            Blocks.ROOTED_DIRT.defaultBlockState();

                    BlockPos abovePos = groundPos.above();
                    removeThinCover(snare, abovePos);

                    // Convert ground block without triggering drops from nearby temporary cover.
                    snare.level.setBlock(groundPos, newBlock, TEMPORARY_BLOCK_FLAGS);

                    if (snare.level.random.nextBoolean()) {
                        snare.level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR,
                                groundPos.getX() + 0.5, groundPos.getY() + 1.0, groundPos.getZ() + 0.5,
                                1, 0.2, 0.15, 0.2, 0.01);
                    }
                    // Use the original surface, before moss/rooted dirt replaced it.
                    snare.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, currentState),
                            groundPos.getX() + 0.5, groundPos.getY() + 1.05, groundPos.getZ() + 0.5,
                            3, 0.2, 0.04, 0.2, 0.04);

                    snare.level.playSound(null, groundPos, SoundEvents.MOSS_CARPET_PLACE,
                            SoundSource.BLOCKS, 0.3f, 1.2f);
                }
            }
        }

        // Stun entities within STUN_RADIUS from center (only check once all blocks are placed).
        if (radius == MAX_RADIUS) {
            stunEntitiesInArea(snare);
        }
    }

    private static void spawnVegetation(RootSnareInstance snare, BlockPos pos, BlockState plant,
                                        SnareVegetationLayout.Plant layout, int revealTick) {
        boolean doublePlant = plant.getBlock() instanceof DoublePlantBlock;
        if (snare.vegetation.containsKey(pos) || !canPrepareVegetation(snare.level, pos)
                || (doublePlant && !canPrepareVegetation(snare.level, pos.above()))) return;
        List<SnareVegetationDisplay> displays = new ArrayList<>(doublePlant ? 2 : 1);
        for (int half = 0; half < (doublePlant ? 2 : 1); half++) {
            BlockState part = doublePlant ? plant.setValue(DoublePlantBlock.HALF,
                    half == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER) : plant;
            SnareVegetationDisplay display = new SnareVegetationDisplay(snare.level, part,
                    layout.scale(), half, layout.windDirection());
            display.setPos(pos.getX() + 0.5 + layout.offsetX(), pos.getY() + 0.01,
                    pos.getZ() + 0.5 + layout.offsetZ());
            display.setYRot(layout.yaw());
            if (!snare.level.addFreshEntity(display)) {
                display.discard();
                displays.forEach(SnareVegetationDisplay::discard);
                return;
            }
            displays.add(display);
        }
        snare.vegetation.put(pos, new VegetationInstance(displays, layout, snare.totalTicks,
                revealTick));
    }

    private static boolean canPrepareVegetation(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        // Displays wait below replaceable cover until the ground wave clears it.
        return state.isAir() || (state.canBeReplaced() && !state.liquid() && !state.isSolidRender(level, pos));
    }

    private static class VegetationInstance {
        final List<SnareVegetationDisplay> displays;
        final SnareVegetationLayout.Plant layout;
        final int spawnTick, revealTick;
        int growthStage;
        int nextIdleTick;
        final int withdrawTick;
        int discardTick = Integer.MAX_VALUE;
        boolean withdrawing;

        VegetationInstance(List<SnareVegetationDisplay> displays, SnareVegetationLayout.Plant layout,
                           int spawnTick, int revealTick) {
            this.displays = List.copyOf(displays);
            this.layout = layout;
            this.spawnTick = spawnTick;
            this.revealTick = revealTick;
            // Reverse the growth rings; finish the center before bindings withdraw.
            withdrawTick = DURATION_TICKS - RootSnareModel.WITHDRAW_LEAD_TICKS
                    - VEGETATION_WITHDRAW_TICKS - 2
                    - (revealTick / GROWTH_DELAY_TICKS) * VEGETATION_WITHDRAW_RING_DELAY;
            nextIdleTick = growthStart() + 8 + Math.floorMod((int) (layout.phase() * 3), 8);
        }

        int growthStart() {
            // Only the center needs a tracking lead; outer rings are staged in advance.
            return Math.max(revealTick, spawnTick + VEGETATION_TRACKING_LEAD);
        }

        void animate(float growth, float tilt, int duration) {
            for (SnareVegetationDisplay display : displays) {
                if (!display.isRemoved()) display.animate(growth, tilt, duration);
            }
        }

        void discard() {
            displays.forEach(SnareVegetationDisplay::discard);
        }
    }

    private static void clearBindingFoliage(RootSnareInstance snare, LivingEntity target) {
        double clearance = Math.max(RootSnareModel.MIN_TARGET_WIDTH, target.getBbWidth()) * 0.6 + 0.25;
        Iterator<VegetationInstance> plants = snare.vegetation.values().iterator();
        while (plants.hasNext()) {
            VegetationInstance plant = plants.next();
            SnareVegetationDisplay display = plant.displays.getFirst();
            double dx = display.getX() - target.getX(), dz = display.getZ() - target.getZ();
            if (dx * dx + dz * dz < clearance * clearance
                    && Math.abs(display.getY() - target.getY()) < 1) {
                plant.discard();
                plants.remove();
            }
        }
    }

    private static void tickVegetation(RootSnareInstance snare) {
        Iterator<Map.Entry<BlockPos, VegetationInstance>> plants = snare.vegetation.entrySet().iterator();
        while (plants.hasNext()) {
            Map.Entry<BlockPos, VegetationInstance> entry = plants.next();
            VegetationInstance plant = entry.getValue();
            if (snare.totalTicks >= plant.discardTick
                    || plant.displays.stream().anyMatch(SnareVegetationDisplay::isRemoved)) {
                plant.discard();
                plants.remove();
                continue;
            }
            int age = snare.totalTicks - plant.growthStart();
            if (!plant.withdrawing && snare.totalTicks >= plant.withdrawTick) {
                plant.animate(0.02f, 0, VEGETATION_WITHDRAW_TICKS);
                plant.withdrawing = true;
                plant.discardTick = snare.totalTicks + VEGETATION_WITHDRAW_TICKS + 1;
            }
            if (plant.withdrawing) continue;
            if (plant.growthStage == 0 && age >= 0
                    && (!snare.originalBlocks.containsKey(entry.getKey().below())
                    || !snare.level.getBlockState(entry.getKey()).isAir()
                    || (plant.displays.size() == 2
                    && !snare.level.getBlockState(entry.getKey().above()).isAir()))) {
                // Terrain may have changed since this ring was prepared.
                plant.discard();
                plants.remove();
                continue;
            }
            if (age >= 0 && plant.growthStage == 0) {
                plant.animate(plant.layout.overshoot(), 0, 4);
                plant.growthStage = 1;
            } else if (age >= 4 && plant.growthStage == 1) {
                plant.animate(0.97f, 0, 2);
                plant.growthStage = 2;
            } else if (age >= 6 && plant.growthStage == 2) {
                plant.animate(1, 0, 2);
                plant.growthStage = 3;
            } else if (age >= 8 && snare.totalTicks >= plant.nextIdleTick) {
                float amplitude = plant.layout.species() == SnareVegetationLayout.Species.SHORT_GRASS ? 0.025f : 0.04f;
                float tilt = (float) Math.sin(snare.totalTicks * 0.035 + plant.layout.phase()) * amplitude;
                plant.animate(1, tilt, 22);
                plant.nextIdleTick = snare.totalTicks + 22;
            }
        }
    }

    private static void tickBindings(RootSnareInstance snare) {
        Iterator<Map.Entry<UUID, SnareBindingInstance>> bindings = snare.bindings.entrySet().iterator();
        while (bindings.hasNext()) {
            Map.Entry<UUID, SnareBindingInstance> entry = bindings.next();
            if (!(snare.level.getEntity(entry.getKey()) instanceof LivingEntity target)
                    || !target.isAlive() || entry.getValue().isRemoved()) {
                entry.getValue().discard();
                bindings.remove();
            } else {
                entry.getValue().tick(target, snare.level.getGameTime());
            }
        }
    }

    private static SnareBindingInstance otherBinding(RootSnareInstance owner, UUID target) {
        for (RootSnareInstance snare : ACTIVE_SNARES.values()) {
            if (snare == owner || snare.level != owner.level) continue;
            SnareBindingInstance binding = snare.bindings.get(target);
            if (binding != null && !binding.isRemoved()) return binding;
        }
        return null;
    }

    private static void removeBindings(RootSnareInstance snare) {
        for (Map.Entry<UUID, SnareBindingInstance> entry : snare.bindings.entrySet()) {
            if (otherBinding(snare, entry.getKey()) != entry.getValue()) entry.getValue().discard();
        }
        snare.bindings.clear();
    }

    private static class SnaredEntityState {
        final double originalY;
        final boolean hadNoAi;
        boolean reportedAiReset;
        boolean reportedResetAttempt;

        SnaredEntityState(LivingEntity entity) {
            this.originalY = entity.getY();
            this.hadNoAi = entity instanceof Mob mob && mob.isNoAi();
        }
    }

    private static SnaredEntityState otherEntityState(RootSnareInstance owner, UUID target) {
        for (RootSnareInstance snare : ACTIVE_SNARES.values()) {
            if (snare == owner || snare.level != owner.level) continue;
            SnaredEntityState state = snare.affectedEntities.get(target);
            if (state != null) return state;
        }
        return null;
    }

    private static SnaredEntityState activeEntityState(Mob mob) {
        if (!(mob.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return null;
        for (RootSnareInstance snare : ACTIVE_SNARES.values()) {
            if (snare.level != level || snare.totalTicks >= DURATION_TICKS) continue;
            SnaredEntityState state = snare.affectedEntities.get(mob.getUUID());
            if (state != null) return state;
        }
        return null;
    }

    /**
     * Called by the Mob mixin; only an active server-side capture owns this flag.
     */
    public static boolean preventAiReset(Mob mob) {
        SnaredEntityState state = activeEntityState(mob);
        if (state == null) return false;
        if (!state.reportedResetAttempt) {
            state.reportedResetAttempt = true;
            LOGGER.warn("Root snare: blocked setNoAi(false) for {} ({}). Caller stack follows.",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), mob.getUUID(),
                    new IllegalStateException("Attempt to clear NoAI during an active root snare"));
        }
        if (!mob.isNoAi()) mob.setNoAi(true);
        return true;
    }

    private static void maintainAiLock(Mob mob, SnaredEntityState state) {
        if (mob.isNoAi()) return;
        if (!state.reportedAiReset) {
            state.reportedAiReset = true;
            LOGGER.warn("Root snare: reapplied NoAI for {} ({}). Flag was cleared outside the guarded setter.",
                    BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), mob.getUUID());
        }
        mob.setNoAi(true);
        mob.getNavigation().stop();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCapturedMobTick(EntityTickEvent.Pre event) {
        if (ACTIVE_SNARES.isEmpty() || !(event.getEntity() instanceof Mob mob)) return;
        SnaredEntityState state = activeEntityState(mob);
        if (state != null) maintainAiLock(mob, state);
    }

    private static void removeThinCover(RootSnareInstance snare, BlockPos pos) {
        BlockState state = snare.level.getBlockState(pos);
        if (state.isAir() || state.liquid()) return;
        if (state.isSolidRender(snare.level, pos) || !state.canBeReplaced()) return;

        if (state.getBlock() instanceof DoublePlantBlock
                && state.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.LOWER) {
            BlockPos upperPos = pos.above();
            BlockState upper = snare.level.getBlockState(upperPos);
            if (upper.is(state.getBlock()) && upper.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER) {
                snare.removedCover.putIfAbsent(upperPos, upper);
                snare.level.setBlock(upperPos, Blocks.AIR.defaultBlockState(), TEMPORARY_BLOCK_FLAGS);
            }
        }
        snare.removedCover.putIfAbsent(pos, state);
        snare.level.setBlock(pos, Blocks.AIR.defaultBlockState(), TEMPORARY_BLOCK_FLAGS);
    }

    private static void stunEntitiesInArea(RootSnareInstance snare) {
        // Find all living entities within STUN_RADIUS (6 blocks) from center.
        Vec3 centerVec = Vec3.atCenterOf(snare.center);
        AABB searchBox = new AABB(snare.center).inflate(STUN_RADIUS);
        List<LivingEntity> entities = snare.level.getEntitiesOfClass(LivingEntity.class, searchBox,
                entity -> {
                    if (entity instanceof Player) return false;
                    if (snare.affectedEntities.containsKey(entity.getUUID())) return false;
                    if (!isSnareEligibleGroundTarget(entity)) return false;
                    // Check if within circular radius.
                    double distanceSq = entity.position().distanceToSqr(centerVec);
                    return distanceSq <= (STUN_RADIUS * STUN_RADIUS);
                });

        for (LivingEntity entity : entities) {
            SnaredEntityState state = otherEntityState(snare, entity.getUUID());
            boolean firstCapture = state == null;
            if (firstCapture) state = new SnaredEntityState(entity);
            snare.affectedEntities.put(entity.getUUID(), state);

            // Disable AI for mobs.
            if (entity instanceof Mob mob) {
                mob.setNoAi(true);
                mob.getNavigation().stop();
            }

            // Apply weakness effect (no particles).
            entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DURATION_TICKS, 2, false, false, false));

            // Apply freezing for shivering effect (same as powder snow).
            entity.setTicksFrozen(DURATION_TICKS * 2);

            PacketDistributor.sendToPlayersTrackingEntity(entity, new RootSnareVfxPayload(
                    entity.getId(), DURATION_TICKS - snare.totalTicks, entity.getBbWidth(), entity.getBbHeight()));

            int remainingTicks = DURATION_TICKS - snare.totalTicks;
            SnareBindingInstance binding = otherBinding(snare, entity.getUUID());
            if (binding == null) {
                binding = SnareBindingInstance.create(snare.level, entity, remainingTicks);
            } else {
                binding.extend(snare.level.getGameTime() + remainingTicks);
            }
            if (binding != null) snare.bindings.put(entity.getUUID(), binding);
            snare.level.sendParticles(ParticleTypes.CLOUD,
                    entity.getX(), entity.getY() + 0.3, entity.getZ(), 2,
                    Math.max(0.2, entity.getBbWidth() * 0.35), 0.16,
                    Math.max(0.2, entity.getBbWidth() * 0.35), 0.15);
            clearBindingFoliage(snare, entity);

            // Submerge entity slightly into the ground.
            if (firstCapture) entity.setPos(entity.getX(), entity.getY() - 0.3, entity.getZ());
        }
    }

    private static boolean isSnareEligibleGroundTarget(LivingEntity entity) {
        return entity.onGround();
    }

    private static BlockPos findGroundAtXZ(ServerLevel level, int x, int z, int startY) {
        // Search down and up from the player's ground level to find solid ground. This handles both
        // uneven terrain and floating platforms.

        // Start from slightly above the player's position and search down.
        int searchStartY = Math.min(startY + 5, level.getMaxBuildHeight() - 1);
        int searchEndY = Math.max(startY - 15, level.getMinBuildHeight());

        BlockPos lastAirPos = null;

        for (int y = searchStartY; y >= searchEndY; y--) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(checkPos);

            // Found a solid block below air = this is the ground surface.
            if (!state.isAir() && state.isSolidRender(level, checkPos)) {
                if (lastAirPos != null || y == startY) {
                    // This is the top of a solid surface, or at the player's level.
                    return checkPos;
                }
                // If no air above and not at player level, keep searching down for a proper
                // surface.
            } else if (!state.isAir() && !state.isSolidRender(level, checkPos)) {
                // Non-solid block like water, grass, etc - keep searching.
                lastAirPos = checkPos;
            } else {

                lastAirPos = checkPos;
            }
        }

        // If we didn't find a surface with air above it, just return the first solid block we find.
        for (int y = searchStartY; y >= searchEndY; y--) {
            BlockPos checkPos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(checkPos);

            if (!state.isAir() && state.isSolidRender(level, checkPos)) {
                return checkPos;
            }
        }

        return null;
    }

    private static List<BlockOffset> getOffsetsAtRadius(int radius) {
        if (radius < 0 || radius >= RING_OFFSETS.length) {
            return List.of();
        }
        return RING_OFFSETS[radius];
    }

    @SuppressWarnings("unchecked")
    private static List<BlockOffset>[] createRingOffsets() {
        List<BlockOffset>[] offsets = new List[MAX_RADIUS + 1];

        for (int radius = 0; radius <= MAX_RADIUS; radius++) {
            List<BlockOffset> positions = new ArrayList<>();
            // Generate circular pattern instead of square.
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {

                    double distance = Math.sqrt(x * x + z * z);

                    // Only include blocks at this radius (forming a growing ring). Use a small
                    // tolerance for circular shape.
                    if (distance >= radius - 0.5 && distance <= radius + 0.5) {
                        positions.add(new BlockOffset(x, z));
                    }
                }
            }
            offsets[radius] = List.copyOf(positions);
        }

        return offsets;
    }

    private static void removeVegetation(RootSnareInstance snare) {
        // Normal expiry follows the outer-to-inner withdrawal; interruption discards immediately.
        for (VegetationInstance vegetation : snare.vegetation.values()) {
            vegetation.discard();
        }
        snare.vegetation.clear();
    }

    private static void restoreBlocks(RootSnareInstance snare) {
        removeBindings(snare);
        removeVegetation(snare);

        // Restore all converted blocks in the correct dimension.
        for (Map.Entry<BlockPos, BlockState> entry : snare.originalBlocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState originalState = entry.getValue();
            BlockState currentState = snare.level.getBlockState(pos);

            // Only restore if still one of our transformed blocks.
            if (currentState.is(Blocks.MOSS_BLOCK) || currentState.is(Blocks.ROOTED_DIRT)) {
                snare.level.setBlock(pos, originalState, TEMPORARY_BLOCK_FLAGS);
            }
        }

        restoreRemovedCover(snare);

        // Restore affected entities.
        Iterator<Map.Entry<UUID, SnaredEntityState>> affected = snare.affectedEntities.entrySet().iterator();
        while (affected.hasNext()) {
            Map.Entry<UUID, SnaredEntityState> entry = affected.next();
            // Release ownership before restoring so the setter guard permits final cleanup.
            affected.remove();
            if (otherEntityState(snare, entry.getKey()) != null) continue;
            if (snare.level.getEntity(entry.getKey()) instanceof LivingEntity entity) {
                restoreEntity(entity, entry.getValue());
                if (otherBinding(snare, entry.getKey()) == null) {
                    PacketDistributor.sendToPlayersTrackingEntity(entity,
                            new RootSnareVfxPayload(entity.getId(), 0, entity.getBbWidth(), entity.getBbHeight()));
                }
            }
        }

        snare.level.playSound(null, snare.center, SoundEvents.MOSS_BREAK,
                SoundSource.BLOCKS, 0.6f, 0.9f);
    }

    private static void restoreEntity(LivingEntity entity, SnaredEntityState state) {
        if (entity instanceof Mob mob) {
            mob.setNoAi(state.hadNoAi);
        }

        entity.removeEffect(MobEffects.WEAKNESS);
        entity.setTicksFrozen(0);
        entity.setPos(entity.getX(), state.originalY, entity.getZ());
    }

    private static void restoreRemovedCover(RootSnareInstance snare) {
        if (snare.removedCover.isEmpty()) return;

        // Restore lower halves first so the upper halves of tall plants can survive.
        List<Map.Entry<BlockPos, BlockState>> cover = new ArrayList<>(snare.removedCover.entrySet());
        cover.sort(Comparator.comparingInt(entry -> entry.getKey().getY()));
        for (Map.Entry<BlockPos, BlockState> entry : cover) {
            BlockPos pos = entry.getKey();
            BlockState coverState = entry.getValue();
            BlockState currentState = snare.level.getBlockState(pos);

            if ((currentState.isAir() || currentState.canBeReplaced())
                    && coverState.canSurvive(snare.level, pos)) {
                snare.level.setBlock(pos, coverState, TEMPORARY_BLOCK_FLAGS);
            }
        }

        snare.removedCover.clear();
    }

    private static void cleanup(UUID playerId) {
        RootSnareInstance existing = ACTIVE_SNARES.remove(playerId);
        if (existing != null) {
            restoreBlocks(existing);
        }
    }

    private static BlockPos findGroundBelow(ServerLevel level, BlockPos start) {
        // Start from below the player's feet.
        BlockPos checkPos = start.below();

        // Search down up to 10 blocks to find the first solid ground.
        for (int i = 0; i < 10; i++) {
            BlockPos pos = checkPos.below(i);
            BlockState state = level.getBlockState(pos);

            if (!state.isAir() && state.isSolidRender(level, pos)) {
                return pos;
            }
        }

        return null;
    }

    public static void cleanupAll(ServerLevel level) {
        Iterator<Map.Entry<UUID, RootSnareInstance>> iterator = ACTIVE_SNARES.entrySet().iterator();

        while (iterator.hasNext()) {
            RootSnareInstance snare = iterator.next().getValue();
            if (snare.level == level) {
                restoreBlocks(snare);
                iterator.remove();
            }
        }
    }

    private static void cleanupSnaresIntersectingChunk(ServerLevel level, ChunkPos chunkPos) {
        Iterator<Map.Entry<UUID, RootSnareInstance>> iterator = ACTIVE_SNARES.entrySet().iterator();

        while (iterator.hasNext()) {
            RootSnareInstance snare = iterator.next().getValue();
            if (snare.level == level && intersectsChunk(snare, chunkPos)) {
                restoreBlocks(snare);
                iterator.remove();
            }
        }
    }

    private static boolean intersectsChunk(RootSnareInstance snare, ChunkPos chunkPos) {
        // Scaled assemblies and field plants may cross the ability's own chunk bounds.
        for (SnareBindingInstance binding : snare.bindings.values()) {
            if (binding.intersects(chunkPos)) return true;
        }
        for (BlockPos pos : snare.vegetation.keySet()) {
            if (new ChunkPos(pos).equals(chunkPos)) return true;
        }
        int minX = snare.center.getX() - STUN_RADIUS;
        int maxX = snare.center.getX() + STUN_RADIUS;
        int minZ = snare.center.getZ() - STUN_RADIUS;
        int maxZ = snare.center.getZ() + STUN_RADIUS;

        return maxX >= chunkPos.getMinBlockX()
                && minX <= chunkPos.getMaxBlockX()
                && maxZ >= chunkPos.getMinBlockZ()
                && minZ <= chunkPos.getMaxBlockZ();
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            cleanupSnaresIntersectingChunk(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            cleanupAll(level);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            cleanupAll(level);
        }
    }
}
