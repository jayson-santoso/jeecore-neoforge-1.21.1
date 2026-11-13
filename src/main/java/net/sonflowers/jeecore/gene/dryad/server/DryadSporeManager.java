package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.core.BlockPos;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModEntities;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.dryad.effect.SporeGuardEffect;
import net.sonflowers.jeecore.gene.dryad.entity.DryadSporeEntity;

import java.util.HashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Owns server spore release, collection, and combat timing; clears tracked state when the server
 * stops.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class DryadSporeManager {
    public static final int RELEASE_INTERVAL = 160;
    public static final int COMBAT_DURATION = 600;
    public static final float PERIODIC_RELEASE_CHANCE = 0.40f;
    public static final float DAMAGE_RELEASE_CHANCE = 0.30f;
    public static final float LOW_HEALTH_THRESHOLD = 0.40f;
    public static final int MAX_SPORES = 2;
    public static final int PICKUP_COOLDOWN = 100;
    private static final Map<UUID, Production> PRODUCTION = new HashMap<>();
    private static final Map<UUID, Set<UUID>> SPORES = new HashMap<>();
    private static final Map<UUID, Long> NEXT_PICKUP = new HashMap<>();
    // Include newly added entities in chunks that are not yet publicly tracked. Weak keys avoid
    // retaining discarded or unloaded entities indefinitely.
    private static final Set<DryadSporeEntity> ALL_SPORES = Collections.newSetFromMap(new WeakHashMap<>());

    private static final class Production {
        final ResourceKey<Level> dimension;
        final int playerId;
        int remaining = RELEASE_INTERVAL;
        long combatUntil;

        Production(ServerPlayer player) {
            dimension = player.level().dimension();
            playerId = player.getId();
        }
    }

    private DryadSporeManager() {}

    public static boolean isActiveOwner(ServerPlayer player) {
        return player.isAlive() && !player.hasDisconnected() && !player.isCreative()
                && !player.isSpectator() && GeneHelper.getPlayerGene(player) == Gene.DRYAD;
    }

    public static void tick(ServerPlayer player) {
        if (!isActiveOwner(player)) return;
        Production state = PRODUCTION.computeIfAbsent(player.getUUID(), ignored -> new Production(player));
        if (state.playerId != player.getId() || state.dimension != player.level().dimension()) return;
        if (player.getServer().overworld().getGameTime() >= state.combatUntil) {
            state.remaining = RELEASE_INTERVAL;
            return;
        }
        if (player.getRemainingFireTicks() > 0) return;
        if (--state.remaining > 0) return;
        // Every attempt consumes the full interval, including failed rolls and unavailable
        // placement. Retrying a failed roll would inflate its chance.
        state.remaining = RELEASE_INTERVAL;
        if (player.getRandom().nextFloat() < PERIODIC_RELEASE_CHANCE) release(player);
    }

    @SubscribeEvent
    public static void onDamageTaken(LivingDamageEvent.Post event) {
        var victim = event.getEntity();
        if (victim.hasEffect(ModMobEffects.SPORE_GUARD)
                && victim.getAbsorptionAmount() <= 0) {
            victim.removeEffect(ModMobEffects.SPORE_GUARD);
            if (victim instanceof ServerPlayer recipient) SporeVisualManager.syncGuard(recipient);
        }
        // Shield absorption counts as taking/dealing damage; blocked or cancelled hits do not.
        if (event.getNewDamage() + event.getReduction(DamageContainer.Reduction.ABSORPTION) <= 0) return;
        if (event.getSource().getEntity() instanceof ServerPlayer attacker && isActiveOwner(attacker)) {
            enterCombat(attacker);
        }
        if (!(victim instanceof ServerPlayer player) || !isActiveOwner(player)) return;
        enterCombat(player);
        if (player.getHealth() >= player.getMaxHealth() * LOW_HEALTH_THRESHOLD) return;
        if (player.getRandom().nextFloat() < DAMAGE_RELEASE_CHANCE) release(player);
    }

    private static void enterCombat(ServerPlayer player) {
        Production state = PRODUCTION.computeIfAbsent(player.getUUID(), ignored -> new Production(player));
        if (state.playerId != player.getId() || state.dimension != player.level().dimension()) {
            state = new Production(player);
            PRODUCTION.put(player.getUUID(), state);
        }
        long now = player.getServer().overworld().getGameTime();
        // Refresh the combat window without postponing a roll during sustained combat.
        if (now >= state.combatUntil) state.remaining = RELEASE_INTERVAL;
        state.combatUntil = now + COMBAT_DURATION;
    }

    public static boolean release(ServerPlayer owner) {
        return release(owner, false);
    }

    public static boolean releaseFromCommand(ServerPlayer owner) {
        return release(owner, true);
    }

    private static boolean release(ServerPlayer owner, boolean commandSpawned) {
        if (commandSpawned) {
            if (!owner.isAlive() || owner.hasDisconnected() || owner.isSpectator()) return false;
        } else {
            if (!isActiveOwner(owner) || owner.getRemainingFireTicks() > 0) return false;
            if (SPORES.getOrDefault(owner.getUUID(), Set.of()).size() >= MAX_SPORES) return false;
        }
        ServerLevel level = owner.serverLevel();
        Vec3 start = owner.position().add(0.0, Math.min(1.25, owner.getBbHeight() * 0.7), 0.0);
        if (!clearSpace(level, start)) return false;
        for (int attempt = 0; attempt < 8; attempt++) {
            double angle = owner.getRandom().nextDouble() * Math.PI * 2.0;
            double horizontalSpeed = 0.11 + owner.getRandom().nextDouble() * 0.04;
            double upwardSpeed = 0.28 + owner.getRandom().nextDouble() * 0.06;
            Vec3 inherited = owner.getDeltaMovement();
            Vec3 velocity = new Vec3(Math.cos(angle) * horizontalSpeed, upwardSpeed,
                    Math.sin(angle) * horizontalSpeed)
                    .add(inherited.x, owner.onGround() ? 0 : inherited.y, inherited.z);
            if (!clearSpace(level, start.add(velocity))) continue;
            DryadSporeEntity spore = new DryadSporeEntity(ModEntities.DRYAD_SPORE.get(), level);
            spore.initialize(owner, start, velocity, commandSpawned);
            if (!level.addFreshEntity(spore)) return false;
            if (!commandSpawned) {
                SPORES.computeIfAbsent(owner.getUUID(), ignored -> new HashSet<>()).add(spore.getUUID());
            }
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                    start.x, start.y, start.z, 3, 0.15, 0.12, 0.15, 0.20);
            level.sendParticles(ParticleTypes.CLOUD,
                    start.x, start.y, start.z, 2, 0.12, 0.08, 0.12, 0.08);
            SporeVisualManager.release(owner);
            owner.displayClientMessage(Component.translatable("message.jeecore.spore_released")
                    .withStyle(ChatFormatting.GREEN), true);
            level.playSound(null, owner.blockPosition(), SoundEvents.SHULKER_SHOOT,
                    SoundSource.PLAYERS, 0.45f, 1.8f);
            return true;
        }
        return false;
    }

    public static int clearAll(MinecraftServer server) {
        var spores = new HashSet<DryadSporeEntity>();
        for (DryadSporeEntity spore : ALL_SPORES) {
            if (!spore.isRemoved() && spore.level().getServer() == server) spores.add(spore);
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (var entity : level.getAllEntities()) {
                if (entity instanceof DryadSporeEntity spore && !spore.isRemoved()) spores.add(spore);
            }
        }
        // Remove after traversal so entity callbacks cannot mutate the iteration.
        spores.forEach(DryadSporeEntity::discard);
        ALL_SPORES.removeAll(spores);
        SPORES.clear();
        return spores.size();
    }

    public static boolean isHazard(BlockState state) {
        return !state.getFluidState().isEmpty() || state.is(BlockTags.FIRE)
                || state.is(Blocks.CACTUS) || state.is(Blocks.MAGMA_BLOCK)
                || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.SWEET_BERRY_BUSH);
    }

    public static boolean clearSpace(ServerLevel level, Vec3 position) {
        double radius = DryadSporeEntity.SIZE / 2.0;
        AABB box = new AABB(position.x - radius, position.y, position.z - radius,
                position.x + radius, position.y + DryadSporeEntity.SIZE, position.z + radius);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ),
                BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            if (!level.hasChunkAt(pos)) return false;
            var state = level.getBlockState(pos);
            if (isHazard(state)) return false;
        }
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(box);
    }

    public static boolean canCollect(ServerPlayer owner, ServerPlayer target) {
        if (!target.isAlive() || target.hasDisconnected() || target.isCreative() || target.isSpectator()) return false;
        if (owner != target && owner.getTeam() != null && !owner.isAlliedTo(target)) return false;
        long now = target.getServer().overworld().getGameTime();
        return now >= NEXT_PICKUP.getOrDefault(target.getUUID(), Long.MIN_VALUE)
                && SporeGuardEffect.canProtect(target);
    }

    public static boolean collect(ServerPlayer owner, ServerPlayer target) {
        if (!canCollect(owner, target) || !SporeGuardEffect.protect(target, owner)) return false;
        NEXT_PICKUP.put(target.getUUID(), target.getServer().overworld().getGameTime() + PICKUP_COOLDOWN);
        target.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                target.getX(), target.getY() + 0.8, target.getZ(), 5, 0.1, 0.15, 0.1, 1.0);
        target.getFoodData().eat(2, 1.0f); // 2 hunger points and 4 saturation points.
        target.getPersistentData().remove("jeecore_spore_food_credit");
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.GENERIC_EAT,
                SoundSource.PLAYERS, 0.45f, 1.8f);
        target.serverLevel().playSound(null, target.blockPosition(), SoundEvents.ALLAY_ITEM_TAKEN,
                SoundSource.PLAYERS, 0.55f, 1.7f);
        SporeVisualManager.syncGuard(target);
        return true;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ALL_SPORES.removeIf(spore -> spore.isRemoved());
        // This also runs after switching away from Dryad, when its passive stops ticking.
        var iterator = PRODUCTION.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            ServerPlayer owner = event.getServer().getPlayerList().getPlayer(entry.getKey());
            Production state = entry.getValue();
            if (owner != null && isActiveOwner(owner) && owner.getId() == state.playerId
                    && owner.level().dimension() == state.dimension) continue;
            iterator.remove();
            Set<UUID> ids = SPORES.remove(entry.getKey());
            ServerLevel level = event.getServer().getLevel(state.dimension);
            if (ids != null && level != null) {
                for (UUID id : ids) {
                    var entity = level.getEntity(id);
                    if (entity instanceof DryadSporeEntity) entity.discard();
                }
            }
        }
        long now = event.getServer().overworld().getGameTime();
        NEXT_PICKUP.values().removeIf(expiry -> expiry <= now);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof DryadSporeEntity spore) {
            ALL_SPORES.add(spore);
        }
    }

    @SubscribeEvent
    public static void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        if (event.getEntity() instanceof DryadSporeEntity spore && spore.isRemoved()) ALL_SPORES.remove(spore);
        if (event.getEntity() instanceof DryadSporeEntity spore && spore.getOwnerUUID() != null) {
            Set<UUID> ids = SPORES.get(spore.getOwnerUUID());
            if (ids != null) {
                ids.remove(spore.getUUID());
                if (ids.isEmpty()) SPORES.remove(spore.getOwnerUUID());
            }
        }
        if (event.getEntity() instanceof Player player) NEXT_PICKUP.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PRODUCTION.clear();
        SPORES.clear();
        NEXT_PICKUP.clear();
        ALL_SPORES.clear();
    }
}
