package net.sonflowers.jeecore.gene.dryad.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.sonflowers.jeecore.gene.dryad.server.DryadSporeManager;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Implements a synchronized spore entity with server movement and collection rules and client
 * interpolation.
 */
public final class DryadSporeEntity extends Entity {
    public static final int LIFETIME_TICKS = 200;
    public static final float SIZE = 0.45f;
    public static final int BLINK_START_TICKS = LIFETIME_TICKS - 40;
    public static final int BLINK_INTERVAL_TICKS = 2;
    public static final int MIN_PICKUP_AGE = 10;
    public static final double GRAVITY = 0.04;
    public static final double AIR_DRAG = 0.99;
    public static final int OWNER_DELAY_TICKS = 30;
    private static final EntityDataAccessor<Long> SPAWN_TIME =
            SynchedEntityData.defineId(DryadSporeEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Boolean> LANDED =
            SynchedEntityData.defineId(DryadSporeEntity.class, EntityDataSerializers.BOOLEAN);
    @Nullable private UUID ownerUUID;
    private boolean consumed;
    private boolean commandSpawned;
    private int lerpSteps;
    private Vec3 lerpTarget = Vec3.ZERO;

    public DryadSporeEntity(EntityType<? extends DryadSporeEntity> type, Level level) {
        super(type, level);
    }

    public void initialize(ServerPlayer owner, Vec3 start, Vec3 velocity) {
        initialize(owner, start, velocity, false);
    }

    public void initialize(ServerPlayer owner, Vec3 start, Vec3 velocity, boolean commandSpawned) {
        ownerUUID = owner.getUUID();
        this.commandSpawned = commandSpawned;
        entityData.set(SPAWN_TIME, level().getGameTime());
        entityData.set(LANDED, false);
        setPos(start);
        setDeltaMovement(velocity);
    }

    public boolean hasLanded() {
        return entityData.get(LANDED);
    }

    @Nullable
    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public int getSporeAge() {
        return (int) Math.max(0, Math.min(LIFETIME_TICKS, level().getGameTime() - entityData.get(SPAWN_TIME)));
    }

    public boolean isBlinkHidden() {
        int age = getSporeAge();
        return age >= LIFETIME_TICKS
                || (age >= BLINK_START_TICKS && ((age - BLINK_START_TICKS) / BLINK_INTERVAL_TICKS) % 2 != 0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SPAWN_TIME, 0L);
        builder.define(LANDED, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(position().lerp(lerpTarget, 1.0 / lerpSteps--));
            }
            return;
        }
        ServerLevel level = (ServerLevel) level();
        int age = getSporeAge();
        if (consumed || age >= LIFETIME_TICKS || ownerUUID == null) {
            discard();
            return;
        }
        if (!(level.getPlayerByUUID(ownerUUID) instanceof ServerPlayer owner)
                || !owner.isAlive() || owner.hasDisconnected() || owner.isSpectator()
                || (!commandSpawned && !DryadSporeManager.isActiveOwner(owner)) || distanceToSqr(owner) > 24 * 24) {
            discard();
            return;
        }
        if (hasLanded() && !hasSupport(level)) {
            // Removing the floor lets the spore fall again; it cannot hover in midair.
            entityData.set(LANDED, false);
            setDeltaMovement(Vec3.ZERO);
        }
        if (!hasLanded()) tickFlight(level);
        if (isRemoved()) return;
        if (!DryadSporeManager.clearSpace(level, position())) {
            discard();
            return;
        }
        if (!hasLanded() || age < MIN_PICKUP_AGE) return;
        Vec3 center = position().add(0, getBbHeight() / 2.0, 0);
        var candidates = level.getEntitiesOfClass(ServerPlayer.class, getBoundingBox().inflate(1.0),
                target -> (target != owner || age >= OWNER_DELAY_TICKS)
                        && DryadSporeManager.canCollect(owner, target)
                        && canReach(target, center));
        candidates.sort(Comparator.<ServerPlayer>comparingDouble(target -> target.distanceToSqr(this))
                .thenComparingInt(Entity::getId));
        for (ServerPlayer target : candidates) {
            if (!DryadSporeManager.collect(owner, target)) continue;
            consumed = true;
            discard();
            return;
        }
    }

    private void tickFlight(ServerLevel level) {
        applyGravity();
        Vec3 motion = getDeltaMovement();
        // Check the swept volume before vanilla collision queries can load chunks.
        AABB sweep = getBoundingBox().expandTowards(motion).inflate(0.01);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(sweep.minX, sweep.minY, sweep.minZ),
                BlockPos.containing(sweep.maxX, sweep.maxY, sweep.maxZ))) {
            if (!level.hasChunkAt(pos)) {
                discard();
                return;
            }
        }
        Vec3 before = position();
        // Vanilla's swept bounding-box collision respects walls, ceilings, slabs, and stairs,
        // including impacts faster than one block per tick.
        move(MoverType.SELF, motion);
        if (verticalCollisionBelow) {
            land(level);
            return;
        }
        Vec3 actual = position().subtract(before);
        setDeltaMovement(new Vec3(Mth.equal(actual.x, motion.x) ? motion.x * AIR_DRAG : 0,
                verticalCollision ? 0 : motion.y * AIR_DRAG,
                Mth.equal(actual.z, motion.z) ? motion.z * AIR_DRAG : 0));
    }

    private boolean hasSupport(ServerLevel level) {
        Vec3 downward = new Vec3(0, -0.05, 0);
        Vec3 allowed = Entity.collideBoundingBox(this, downward, getBoundingBox(), level, List.of());
        return allowed.y > downward.y && !DryadSporeManager.isHazard(level.getBlockState(getOnPos()));
    }

    private void land(ServerLevel level) {
        BlockPos support = getOnPos();
        var state = level.getBlockState(support);
        if (DryadSporeManager.isHazard(state) || state.isAir()) {
            discard();
            return;
        }
        entityData.set(LANDED, true);
        setDeltaMovement(Vec3.ZERO);
        // Five particles total; use the actual supporting block without breaking it.
        level.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.05, getZ(),
                2, 0.1, 0.03, 0.1, 0.06);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                getX(), getY() + 0.03, getZ(), 3, 0.12, 0.02, 0.12, 0.025);
        level.playSound(null, blockPosition(), SoundEvents.AZALEA_LEAVES_FALL,
                SoundSource.PLAYERS, 0.5f, 0.8f);
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    protected MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    @Override
    public boolean isSuppressingBounce() {
        return true;
    }

    private boolean canReach(ServerPlayer player, Vec3 center) {
        AABB box = player.getBoundingBox();
        Vec3 nearest = new Vec3(Mth.clamp(center.x, box.minX, box.maxX),
                Mth.clamp(center.y, box.minY, box.maxY), Mth.clamp(center.z, box.minZ, box.maxZ));
        if (nearest.distanceToSqr(center) > 1.0) return false;
        // Trace from inside the player's body, so a boundary intersection cannot collect through a
        // wall.
        Vec3 from = new Vec3(player.getX(), nearest.y, player.getZ());
        return level().clip(new ClipContext(from, center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this)).getType() == HitResult.Type.MISS;
    }

    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        lerpTarget = new Vec3(x, y, z);
        lerpSteps = Math.max(1, steps);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    // The registered type is nonsaving; keep manual summon/load data harmless.
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
}
