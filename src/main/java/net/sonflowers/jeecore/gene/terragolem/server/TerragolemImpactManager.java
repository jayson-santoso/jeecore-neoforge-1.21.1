package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.terragolem.effect.ConcussionEffect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Tracks server landings and emits impact waves once per landing without changing vanilla fall
 * damage.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class TerragolemImpactManager {
    public static final int CONCUSSION_TICKS = 60;
    public static final float DAMAGE = 2;
    public static final double LAUNCH_SPEED = 0.38; // About one block of rise under vanilla gravity/drag.
    private static final List<Wave> WAVES = new ArrayList<>();
    private static final Map<UUID, FallTrace> FALLS = new HashMap<>();

    private static final class FallTrace {
        final ServerLevel level;
        final int entityId;
        Vec3 previous;
        double peak;
        boolean airborne, consumed;
        FallTrace(ServerPlayer player) {
            level = player.serverLevel(); entityId = player.getId(); previous = player.position(); peak = player.getY();
            airborne = !player.onGround();
        }
    }

    private record Surface(BlockPos pos, BlockState state, double top, double distance) {}
    private static final class Wave {
        final ServerLevel level;
        final ServerPlayer source;
        final Vec3 center;
        final double radius;
        final long started;
        final Map<Long, Surface> surfaces;
        final Set<Long> revealed = new HashSet<>();
        final Set<UUID> hit = new HashSet<>();
        long lastTick = Long.MIN_VALUE;
        Wave(ServerPlayer source, double radius, Map<Long, Surface> surfaces) {
            this.level = source.serverLevel(); this.source = source; this.center = source.position();
            this.radius = radius; this.surfaces = surfaces; this.started = level.getGameTime();
        }
    }

    private TerragolemImpactManager() {}

    public static double radiusForFall(float distance) {
        return distance < 6 || !Float.isFinite(distance) ? 0 : 4 + Mth.clamp((distance - 6) / 6.0, 0, 1) * 2;
    }

    @SubscribeEvent public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FallTrace trace = FALLS.get(player.getUUID());
        if (trace != null && trace.level == player.level() && trace.entityId == player.getId() && trace.airborne) {
            if (!trace.consumed) trace.consumed = release(player, (float) Math.max(event.getDistance(), trace.peak - player.getY()));
        } else release(player, event.getDistance());
    }

    private static boolean eligible(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().flying && !player.isFallFlying()
                && !player.isPassenger() && !player.isInWaterOrBubble() && !player.isInLava()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM;
    }

    @SubscribeEvent public static void beforePlayerTick(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!eligible(player)) { FALLS.remove(player.getUUID()); return; }
        FallTrace trace = FALLS.get(player.getUUID());
        if (trace == null || trace.level != player.level() || trace.entityId != player.getId()
                || trace.previous.distanceToSqr(player.position()) > 64) {
            FALLS.put(player.getUUID(), new FallTrace(player)); return;
        }
        trace.previous = player.position();
        if (!player.onGround()) { trace.airborne = true; trace.peak = Math.max(trace.peak, player.getY()); }
    }

    @SubscribeEvent public static void afterPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FallTrace trace = FALLS.get(player.getUUID());
        if (trace == null) return;
        if (!eligible(player)) { FALLS.remove(player.getUUID()); return; }
        if (player.onGround()) {
            if (trace.airborne && !trace.consumed) release(player, (float) (trace.peak - player.getY()));
            trace.peak = player.getY(); trace.airborne = trace.consumed = false;
        } else { trace.airborne = true; trace.peak = Math.max(trace.peak, player.getY()); }
    }

    /**
     * Called by the actual landing event; does not change fall damage or the ability cooldowns.
     */
    public static boolean release(ServerPlayer player, float fallDistance) {
        double radius = radiusForFall(fallDistance);
        if (radius == 0 || !eligible(player)) return false;
        ServerLevel level = player.serverLevel();
        Surface support = findSurface(level, player.getBlockX(), player.getBlockZ(), player.getY(), player.position());
        if (support == null || Math.abs(player.getY() - support.top) > 0.25) return false;
        // Several hooks can observe the same landing; only emit it once.
        if (WAVES.stream().anyMatch(wave -> wave.source == player && wave.started == level.getGameTime())) return false;
        Map<Long, Surface> surfaces = collectSurfaces(level, support, player.position(), radius);
        WAVES.add(new Wave(player, radius, surfaces));
        level.playSound(null, player.blockPosition(), SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1, 0.65f);
        return true;
    }

    private static Surface findSurface(ServerLevel level, int x, int z, double feetY, Vec3 center) {
        int baseY = Mth.floor(feetY);
        for (int y = baseY; y >= baseY - 2; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!level.hasChunkAt(pos)) return null;
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().isEmpty()) return null;
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;
            double top = y + shape.max(Direction.Axis.Y);
            if (top > feetY + 1.01) continue;
            if (state.getRenderShape() != RenderShape.MODEL || state.hasBlockEntity()
                    || !level.getFluidState(pos.above()).isEmpty()
                    || !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()) return null;
            double distance = Math.sqrt(Mth.square(x + 0.5 - center.x) + Mth.square(z + 0.5 - center.z));
            return new Surface(pos, state, top, distance);
        }
        return null;
    }

    private static Map<Long, Surface> collectSurfaces(ServerLevel level, Surface start, Vec3 center, double radius) {
        Map<Long, Surface> surfaces = new HashMap<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Surface> queue = new ArrayDeque<>();
        surfaces.put(column(start.pos), start); visited.add(column(start.pos)); queue.add(start);
        while (!queue.isEmpty()) {
            Surface previous = queue.removeFirst();
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos next = previous.pos.relative(side);
                long key = column(next);
                if (!visited.add(key)) continue;
                double distance = Math.sqrt(Mth.square(next.getX() + 0.5 - center.x) + Mth.square(next.getZ() + 0.5 - center.z));
                if (distance > radius) continue;
                Surface surface = findSurface(level, next.getX(), next.getZ(), center.y, center);
                if (surface == null || Math.abs(surface.top - previous.top) > 1.01
                        || Math.abs(surface.top - start.top) > 1.01) continue;
                surfaces.put(key, surface); queue.add(surface);
            }
        }
        return surfaces;
    }

    private static long column(BlockPos pos) { return BlockPos.asLong(pos.getX(), 0, pos.getZ()); }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        Iterator<Wave> waves = WAVES.iterator();
        while (waves.hasNext()) {
            Wave wave = waves.next();
            long now = wave.level.getGameTime();
            if (now == wave.lastTick) continue;
            wave.lastTick = now;
            double front = Math.min(wave.radius, (now - wave.started + 1) * 0.5);
            double previousFront = (now - wave.started) * 0.5;
            for (var entry : wave.surfaces.entrySet()) {
                Surface surface = entry.getValue();
                if (surface.distance > front || !wave.revealed.add(entry.getKey())) continue;
                if (unchanged(wave, surface)) {
                    wave.level.addFreshEntity(new ImpactRippleDisplay(wave.level, surface.pos, surface.state));
                    wave.level.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, surface.state),
                            surface.pos.getX() + 0.5, surface.top + 0.03, surface.pos.getZ() + 0.5,
                            1, 0.18, 0.02, 0.18, 0.06);
                }
            }
            AABB area = new AABB(wave.center.x - wave.radius, wave.center.y - 1.5, wave.center.z - wave.radius,
                    wave.center.x + wave.radius, wave.center.y + 2, wave.center.z + wave.radius);
            for (LivingEntity target : wave.level.getEntitiesOfClass(LivingEntity.class, area)) {
                if (target == wave.source || !target.isAlive() || target.isSpectator() || !target.onGround()
                        || target.isPassenger() || target.isInWaterOrBubble() || target.isInLava()
                        || (target instanceof Player player && player.getAbilities().invulnerable)
                        || wave.hit.contains(target.getUUID())) continue;
                double distance = Math.sqrt(Mth.square(target.getX() - wave.center.x) + Mth.square(target.getZ() - wave.center.z));
                if (distance > front || (previousFront > 0 && distance <= previousFront)) continue;
                Surface support = wave.surfaces.get(column(target.blockPosition()));
                if (support == null || !unchanged(wave, support) || Math.abs(target.getY() - support.top) > 0.3) continue;
                wave.hit.add(target.getUUID());
                if (target.addEffect(new MobEffectInstance(ModMobEffects.CONCUSSION, CONCUSSION_TICKS, 0,
                        false, false, true), wave.source)) {
                    target.hurt(wave.level.damageSources().playerAttack(wave.source), DAMAGE);
                    if (target.isAlive()) {
                        ConcussionEffect.restrain(target);
                        target.setDeltaMovement(0, LAUNCH_SPEED, 0);
                        target.hurtMarked = true;
                    }
                }
            }
            if (front >= wave.radius) waves.remove();
        }
    }

    private static boolean unchanged(Wave wave, Surface surface) {
        return wave.level.hasChunkAt(surface.pos) && wave.level.getBlockState(surface.pos).equals(surface.state)
                && wave.level.getFluidState(surface.pos).isEmpty() && wave.level.getFluidState(surface.pos.above()).isEmpty();
    }

    @SubscribeEvent public static void unload(LevelEvent.Unload event) {
        WAVES.removeIf(wave -> wave.level == event.getLevel());
        FALLS.values().removeIf(trace -> trace.level == event.getLevel());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { FALLS.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stop(ServerStoppedEvent event) { WAVES.clear(); FALLS.clear(); }
}
