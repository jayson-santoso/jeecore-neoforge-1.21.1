package net.sonflowers.jeecore.gene.terragolem.server;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemResetPayload;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.ability.AbilityHandler;
import net.sonflowers.jeecore.core.server.BeamManager;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemBeamBurstPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemBigStunPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemFlareHoldPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemReadyUpStatePayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns server charge windows, burst and held-beam releases, stun lifetimes, and synchronized
 * ready-up visuals.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class TerragolemReadyUpManager {
    // Charge timing and movement limits.
    public static final int CHARGE_TICKS = 60; // 3 seconds of charging up.
    public static final int WARNING_TICKS = 40; // 2 seconds of blinking warning.
    public static final int READY_UP_DURATION_TICKS = CHARGE_TICKS + WARNING_TICKS;
    public static final int BIG_STUN_TICKS = 100; // 5 second stun after the ready-up times out.
    public static final double CHARGE_HORIZONTAL_LIMIT = 0.02;
    private static final int SEGMENTS = 6; 
    private static final int BLINK_INTERVAL = 4; // Ticks per warning blink toggle.

    private static final ResourceLocation READY_UP_SLOWDOWN_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_ready_up_slowdown");
    private static final ResourceLocation READY_UP_NO_JUMP_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "terragolem_ready_up_no_jump");

    // Player UUID to level game time at which the ready-up state started.
    private static final Map<UUID, Long> READY_UP_PLAYERS = new ConcurrentHashMap<>();
    // Player UUID to frozen charge of the flare held after a right-button press release, until
    // right-button release.
    private static final Map<UUID, Float> HELD_PLAYERS = new ConcurrentHashMap<>();
    // Player UUID to game time at which the post-release stun ends.
    private static final Map<UUID, Long> AFTERMATH_PLAYERS = new ConcurrentHashMap<>();
    // Player UUID to game time at which the bigger timeout stun ends.
    private static final Map<UUID, Long> BIG_STUN_PLAYERS = new ConcurrentHashMap<>();
    // Player UUID to last ready-up action-bar visual state sent to the client.
    private static final Map<UUID, Integer> ACTION_BAR_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, JumpFlight> PLAYER_JUMPS = new ConcurrentHashMap<>();

    private static final class JumpFlight {
        final int entityId;
        final ResourceKey<Level> dimension;
        final long started;
        boolean leftGround;
        JumpFlight(ServerPlayer player) {
            entityId = player.getId();
            dimension = player.level().dimension();
            started = player.level().getGameTime();
        }
    }

    // Charge lifecycle.
    public static void activate(Player player) {
        if (player.level().isClientSide || !player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM) return;
        if (isReadyUp(player.getUUID())) return;

        READY_UP_PLAYERS.put(player.getUUID(), player.level().getGameTime());
        applyChargeAttributes(player);
        AbilityHandler.setPrimaryCooldown(player);
        sync(player, true);
        TerragolemSoundManager.play(player, SoundEvents.DEEPSLATE_BREAK, 0.65f, 0.7f);
    }

    public static void tick(Player player) {
        if (player.level().isClientSide) return;
        if (!player.isAlive()) {
            if (hasTransientState(player)) cancel(player);
            return;
        }
        TerragolemAbilityFuelManager.tickHeldBeam(player);
        long now = player.level().getGameTime();

        Long aftermathExpiry = AFTERMATH_PLAYERS.get(player.getUUID());
        if (aftermathExpiry != null && now >= aftermathExpiry) {
            AFTERMATH_PLAYERS.remove(player.getUUID());
            // Don't strip the attributes if a big stun is still active.
            if (!BIG_STUN_PLAYERS.containsKey(player.getUUID())) {
                removeReadyUpAttributes(player);
            }
        }
        Long bigStunExpiry = BIG_STUN_PLAYERS.get(player.getUUID());
        if (bigStunExpiry != null && now >= bigStunExpiry) {
            BIG_STUN_PLAYERS.remove(player.getUUID());
            // Don't strip the attributes if an aftermath stun is still active.
            if (!AFTERMATH_PLAYERS.containsKey(player.getUUID())) {
                removeReadyUpAttributes(player);
            }
        }

        Long start = READY_UP_PLAYERS.get(player.getUUID());
        if (start == null) return;
        restrainChargeMovement(player);

        long elapsed = now - start;
        if (elapsed > 0 && elapsed < CHARGE_TICKS && elapsed % 10 == 0) {
            float charge = elapsed / (float) CHARGE_TICKS;
            TerragolemSoundManager.play(player, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.18f + charge * 0.12f, 0.7f + charge * 0.5f);
        }
        if (elapsed >= CHARGE_TICKS && elapsed < READY_UP_DURATION_TICKS && elapsed % 8 == 0) {
            TerragolemSoundManager.play(player, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), 1.0f, 0.65f);
            TerragolemSoundManager.play(player, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.35f, 0.85f);
        }
        if (elapsed >= READY_UP_DURATION_TICKS) {
            // No follow-up input received within the window; ready-up ends with the big stun.
            timeout(player);
            return;
        }
        sendActionBar(player, elapsed);
    }

    public static void cancel(Player player) {
        if (player.level().isClientSide) return;
        boolean hadState = hasTransientState(player);
        boolean held = HELD_PLAYERS.remove(player.getUUID()) != null;
        READY_UP_PLAYERS.remove(player.getUUID());
        AFTERMATH_PLAYERS.remove(player.getUUID());
        BIG_STUN_PLAYERS.remove(player.getUUID());
        ACTION_BAR_STATES.remove(player.getUUID());
        PLAYER_JUMPS.remove(player.getUUID());
        if (held) BeamManager.setBeaming(player, false);
        removeReadyUpAttributes(player);
        if (hadState) clearActionBar(player);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TerragolemResetPayload(player.getUUID()));
    }

    private static boolean hasTransientState(Player player) {
        UUID id = player.getUUID();
        return READY_UP_PLAYERS.containsKey(id) || HELD_PLAYERS.containsKey(id)
                || AFTERMATH_PLAYERS.containsKey(id) || BIG_STUN_PLAYERS.containsKey(id)
                || ACTION_BAR_STATES.containsKey(id) || PLAYER_JUMPS.containsKey(id);
    }

    private static boolean needsLifecycleReset(Player player) {
        return !player.level().isClientSide && (GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM
                || hasTransientState(player));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player && needsLifecycleReset(player)) {
            cancel(player);
            TerragolemOverheatManager.clearHeat(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTravel(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof Player player && needsLifecycleReset(player)) cancel(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (needsLifecycleReset(event.getEntity())) cancel(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (needsLifecycleReset(event.getEntity())) cancel(event.getEntity());
    }

    private static void cancel(Player player, boolean expired) {
        if (READY_UP_PLAYERS.remove(player.getUUID()) == null) return;
        removeReadyUpAttributes(player);
        ACTION_BAR_STATES.remove(player.getUUID());
        clearActionBar(player);
        if (expired) {
            spawnReleaseBurst(player);
        }
        sync(player, false);
    }

    public static boolean isReadyUp(UUID uuid) {
        return READY_UP_PLAYERS.containsKey(uuid);
    }

    public static boolean isHeld(UUID uuid) {
        return HELD_PLAYERS.containsKey(uuid);
    }

    public static boolean isInAftermath(UUID uuid) {
        return AFTERMATH_PLAYERS.containsKey(uuid);
    }

    public static int getRemainingTicks(Player player) {
        Long start = READY_UP_PLAYERS.get(player.getUUID());
        if (start == null) return 0;
        return Math.max(0, (int) (READY_UP_DURATION_TICKS - (player.level().getGameTime() - start)));
    }

    public static int getFullyChargedSegments(Player player) {
        Long start = READY_UP_PLAYERS.get(player.getUUID());
        if (start == null) return 0;
        long elapsed = player.level().getGameTime() - start;
        return (int) Math.min(SEGMENTS, Math.max(0, elapsed / (CHARGE_TICKS / SEGMENTS)));
    }

    // Mouse clicks captured client-side; button 0 = left, 1 = right.
    public static void handleClick(Player player, int button, boolean down) {
        if (player.level().isClientSide || !player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM) return;
        if (button == 0) {
            // Left-button release requires at least 1 fully charged segment.
            if (down && isReadyUp(player.getUUID()) && getFullyChargedSegments(player) >= 1) {
                release(player);
            }
            return;
        }
        if (button != 1) return;
        if (down) {
            if (!isReadyUp(player.getUUID())) return;
            int segments = getFullyChargedSegments(player);
            // Right click is only listened to once at least 3 segments are fully charged.
            if (segments < 3) return;
            hold(player, segments);
        } else {
            // right-button release is only listened to after a right-button press (while the flare
            // is held).
            if (isHeld(player.getUUID())) {
                releaseHold(player);
            }
        }
    }

    private static void release(Player player) {
        int segments = getFullyChargedSegments(player);
        boolean jumpBoost = isAirborneFromPlayerJump(player);
        TerragolemAbilityFuelManager.consumeBurst(player, segments);
        TerragolemSoundManager.burst(player, segments);
        // Ends the ready-up vfx and charge bar, plays the flash release burst.
        cancel(player, true);
        // Single-tick burst beam raycast.
        fireBurstBeam(player, segments);
        spawnGroundDustPillar(player);
        // Burst heat scaled by the charged segment tier.
        TerragolemOverheatManager.addBurstHeat(player, segments);
        // Overheat backlash can kill the player synchronously; do not recreate cleared state.
        if (!player.isAlive()) return;
        if (!player.isShiftKeyDown()) {
            Vec3 look = player.getLookAngle();
            double strength = jumpBoost ? (segments <= 2 ? 0.7 : segments <= 4 ? 1.0 : 1.3)
                    : (segments <= 2 ? 0.6 : segments <= 4 ? 1.0 : 1.5);
            // Full aimed propulsion is reserved for a real player-jump flight. Otherwise retain the
            // original horizontal recoil and small upward kick.
            Vec3 recoil = jumpBoost ? look.scale(-strength)
                    : new Vec3(-look.x * strength, 0.2 * strength, -look.z * strength);
            player.addDeltaMovement(recoil);
            if (jumpBoost && player.getDeltaMovement().y > 0) {
                player.setOnGround(false);
                player.fallDistance = 0;
            }
            player.hurtMarked = true;
            player.hasImpulse = true;
        }
        // Post-release stun: 1.5 seconds regardless of charged segments.
        AFTERMATH_PLAYERS.put(player.getUUID(), player.level().getGameTime() + 30);
        applyStunAttributes(player, -0.6);
    }

    // Left-button primary: single-tick raycast beam (100 blocks, stopped by block/entity
    // collision), damage and knockback scaled by the charged segments.
    private static void fireBurstBeam(Player player, int segments) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double range = 100.0;
        Vec3 maxEnd = start.add(look.scale(range));

        BlockHitResult blockHit = player.level().clip(new ClipContext(
                start, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double dist = blockHit.getLocation().distanceTo(start);

        AABB searchBox = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                player.level(), player, start, maxEnd, searchBox,
                e -> !e.isSpectator() && e.isPickable() && e != player);

        LivingEntity target = null;
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity le) {
            double entityDist = entityHit.getLocation().distanceTo(start);
            if (entityDist < dist) {
                dist = entityDist;
                target = le;
            }
        }

        Vec3 end = start.add(look.scale(dist));
        if (target == null) BeamManager.spawnBlockImpactSmoke(player, blockHit);
        if (target != null || blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
            if (player.level() instanceof ServerLevel level) {
                TerragolemSoundManager.playAt(level, end, SoundEvents.DEEPSLATE_BREAK, 0.85f, 0.65f);
                TerragolemSoundManager.playAt(level, end, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.75f, 1.85f);
            }
        }
        boolean impact = target != null;
        if (target != null) {
            // 2 damage (1 heart) per charged segment.
            target.hurt(player.damageSources().playerAttack(player), 2.0f * segments);
            // Knockback along the beam: 1-2 low, 3-4 medium, 5-6 high.
            float knockback = segments <= 2 ? 0.5f : segments <= 4 ? 1.0f : 1.5f;
            target.setDeltaMovement(target.getDeltaMovement().add(look.x * knockback, 0.25 * knockback, look.z * knockback));
            target.hurtMarked = true;
        }

        if (impact && player.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.FLASH, end.x, end.y, end.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }

        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new TerragolemBeamBurstPayload(player.getUUID(), segments, end.x, end.y, end.z, impact));
    }

    private static void spawnGroundDustPillar(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        BlockPos groundPos = BlockPos.containing(
                player.getX(), player.getBoundingBox().minY - 0.05, player.getZ());
        BlockState groundState = serverLevel.getBlockState(groundPos);
        if (groundState.isAir()) {
            groundState = serverLevel.getBlockState(groundPos.below());
        }
        if (groundState.isAir()) return;

        // One batched packet creates a compact, roughly circular 3x3-footprint pillar.
        serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.DUST_PILLAR, groundState),
                player.getX(), player.getBoundingBox().minY + 0.05, player.getZ(),
                30, 1.25, 0.08, 1.25, 0.12);
    }

    // Ends ready-up but freezes the flare render (size and color) until right-button release,
    // keeping the slowness and jump lock for the duration of the hold.
    private static void hold(Player player, int segments) {
        Long start = READY_UP_PLAYERS.get(player.getUUID());
        if (start == null) return;
        float frozenCharge = Math.min(1.0f, (player.level().getGameTime() - start) / (float) CHARGE_TICKS);
        cancel(player, true);
        // Knockback recoil opposite the player's facing (ignored while sneaking).
        if (!player.isShiftKeyDown()) {
            Vec3 look = player.getLookAngle();
            player.addDeltaMovement(new Vec3(-look.x * 0.8, 0.16, -look.z * 0.8));
            player.hurtMarked = true;
        }
        applyStunAttributes(player, -0.6);
        // Keep the beam active for the duration of the hold, with range scaled by the charged
        // segments: 3 -> 20, 4 -> 30, 5 -> 40, 6 -> 50.
        BeamManager.setBeaming(player, true);
        TerragolemSoundManager.play(player, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.85f, 0.65f);
        BeamManager.setBeamRange(player, switch (segments) {
            case 4 -> 30.0;
            case 5 -> 40.0;
            case 6 -> 50.0;
            default -> 20.0;
        });
        HELD_PLAYERS.put(player.getUUID(), frozenCharge);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TerragolemFlareHoldPayload(player.getUUID(), true, frozenCharge));
    }

    private static void releaseHold(Player player) {
        if (HELD_PLAYERS.remove(player.getUUID()) == null) return;
        BeamManager.setBeaming(player, false);
        TerragolemSoundManager.play(player, SoundEvents.BEACON_DEACTIVATE, 0.7f, 0.75f);
        TerragolemSoundManager.play(player, SoundEvents.FIRE_EXTINGUISH, 0.45f, 0.65f);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TerragolemFlareHoldPayload(player.getUUID(), false, 0.0f));
        // 2 second stun after the right click is released.
        AFTERMATH_PLAYERS.put(player.getUUID(), player.level().getGameTime() + 40);
        applyStunAttributes(player, -0.6);
    }

    /**
     * Force-releases a held beam, e.g. when the overheat cap is reached.
     */
    public static void forceReleaseHold(Player player) {
        releaseHold(player);
    }

    // Timeout after the warning blinks: bigger stun, nausea, and a client-side flash/fov hit.
    private static void timeout(Player player) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload(
                            sp.getUUID(), -1.0f, BIG_STUN_TICKS));
        }
        TerragolemSoundManager.play(player, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 1.0f, 0.65f);
        TerragolemSoundManager.play(player, SoundEvents.REDSTONE_TORCH_BURNOUT, 0.65f, 0.6f);
        TerragolemSoundManager.play(player, SoundEvents.FIRE_EXTINGUISH, 0.7f, 0.7f);
        TerragolemAbilityFuelManager.consume(player, TerragolemAbilityFuelManager.TIMEOUT_HUNGER_COST);
        cancel(player, true);
        applyBigStun(player);
        // 8 seconds of extreme nausea, without visible potion particles or HUD icon.
        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 3, false, false, false));
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new TerragolemBigStunPayload());
        }
    }

    /**
     * Applies heavy slowness and jump lock for the supplied duration; also used by overheating.
     */
    public static void applyBigStun(Player player) {
        applyBigStun(player, BIG_STUN_TICKS, -0.85);
    }

    public static void applyBigStun(Player player, int ticks, double speedAmount) {
        BIG_STUN_PLAYERS.put(player.getUUID(), player.level().getGameTime() + ticks);
        applyStunAttributes(player, speedAmount);
    }

    private static void sendActionBar(Player player, long elapsed) {
        if (elapsed < CHARGE_TICKS) {
            int visualState = getChargeBarVisualState(elapsed);
            Integer previousState = ACTION_BAR_STATES.get(player.getUUID());
            if (previousState != null && previousState == visualState) return;
            ACTION_BAR_STATES.put(player.getUUID(), visualState);
            // Charging phase: yellow bar filling segment by segment.
            double charge = (double) elapsed / CHARGE_TICKS;
            StringBuilder bar = new StringBuilder("[");
            for (int i = 0; i < SEGMENTS; i++) {
                double segmentProgress = Math.max(0.0, Math.min(1.0, charge * SEGMENTS - i));
                if (segmentProgress <= 0.0) bar.append('░');
                else if (segmentProgress < 1.0 / 3.0) bar.append('▒');
                else if (segmentProgress < 2.0 / 3.0) bar.append('▓');
                else bar.append('█');
            }
            bar.append(']');
            // Yellow while charging, green once at least 3 segments are fully charged.
            int segments = (int) Math.min(SEGMENTS, elapsed / (CHARGE_TICKS / SEGMENTS));
            ChatFormatting color = segments >= 3 ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
            player.displayClientMessage(Component.literal(bar.toString()).withStyle(color), true);
        } else {
            // Warning phase: fully charged bar blinks red with warning signs.
            boolean visible = (elapsed / BLINK_INTERVAL) % 2 == 0;
            int visualState = visible ? 101 : 100;
            Integer previousState = ACTION_BAR_STATES.get(player.getUUID());
            if (previousState != null && previousState == visualState) return;
            ACTION_BAR_STATES.put(player.getUUID(), visualState);
            if (visible) {
                player.displayClientMessage(Component.literal("⚠ [██████] ⚠").withStyle(ChatFormatting.RED), true);
            } else {
                clearActionBar(player);
            }
        }
    }

    private static int getChargeBarVisualState(long elapsed) {
        int state = elapsed >= CHARGE_TICKS / 2 ? 1 : 0;
        double charge = (double) elapsed / CHARGE_TICKS;
        for (int i = 0; i < SEGMENTS; i++) {
            double progress = Math.max(0.0, Math.min(1.0, charge * SEGMENTS - i));
            int shade = progress <= 0.0 ? 0 : progress < 1.0 / 3.0 ? 1 : progress < 2.0 / 3.0 ? 2 : 3;
            state |= shade << (1 + i * 2);
        }
        return state;
    }

    private static void clearActionBar(Player player) {
        player.displayClientMessage(Component.empty(), true);
    }

    private static void spawnReleaseBurst(Player player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(ParticleTypes.FLASH,
                player.getX(), player.getY() + 2.0, player.getZ(),
                1, 0, 0, 0, 0);
    }

    private static void applyChargeAttributes(Player player) {
        applyStunAttributes(player, -0.95);
        if (!BIG_STUN_PLAYERS.containsKey(player.getUUID())) {
            AttributeInstance jumpStrength = player.getAttribute(Attributes.JUMP_STRENGTH);
            if (jumpStrength != null) jumpStrength.removeModifier(READY_UP_NO_JUMP_ID);
        }
        restrainChargeMovement(player);
    }

    private static void restrainChargeMovement(Player player) {
        player.setDeltaMovement(limitChargeMotion(player.getDeltaMovement()));
        player.setSprinting(false);
    }

    public static Vec3 limitChargeMotion(Vec3 movement) {
        double horizontal = movement.horizontalDistance();
        if (horizontal <= CHARGE_HORIZONTAL_LIMIT) return movement;
        double scale = CHARGE_HORIZONTAL_LIMIT / horizontal;
        return new Vec3(movement.x * scale, movement.y, movement.z * scale);
    }

    @SubscribeEvent
    public static void onChargingPlayerTick(PlayerTickEvent.Post event) {
        if (!event.getEntity().level().isClientSide) {
            isAirborneFromPlayerJump(event.getEntity()); // Reconcile landing/lifecycle even outside charging.
            if (isReadyUp(event.getEntity().getUUID())) restrainChargeMovement(event.getEntity());
        }
    }

    public static void recordPlayerJump(ServerPlayer player) {
        if (player.isAlive() && !player.hasDisconnected() && player.onGround()
                && GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM && !usesOtherFlight(player)) {
            PLAYER_JUMPS.put(player.getUUID(), new JumpFlight(player));
        }
    }

    private static boolean usesOtherFlight(Player player) {
        return player.getAbilities().flying || player.isFallFlying() || player.isPassenger()
                || player.isInWater() || player.isInLava();
    }

    private static boolean isAirborneFromPlayerJump(Player player) {
        JumpFlight flight = PLAYER_JUMPS.get(player.getUUID());
        if (flight == null) return false;
        boolean invalid = flight.entityId != player.getId() || flight.dimension != player.level().dimension()
                || !player.isAlive() || GeneHelper.getPlayerGene(player) != Gene.TERRAGOLEM || usesOtherFlight(player);
        if (invalid || (player.onGround() && (flight.leftGround || player.level().getGameTime() - flight.started > 2))) {
            PLAYER_JUMPS.remove(player.getUUID());
            return false;
        }
        if (player.onGround()) return false;
        flight.leftGround = true;
        return true;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PLAYER_JUMPS.clear();
    }

    @SubscribeEvent
    public static void onChargingJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player player && isReadyUp(player.getUUID())) {
            // Limit sprint-jump momentum to the same tiny shuffle speed.
            restrainChargeMovement(player);
        }
    }

    private static void applyStunAttributes(Player player, double speedAmount) {
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) {
            moveSpeed.removeModifier(READY_UP_SLOWDOWN_ID);
            moveSpeed.addTransientModifier(new AttributeModifier(READY_UP_SLOWDOWN_ID, speedAmount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jumpStrength = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpStrength != null) {
            jumpStrength.removeModifier(READY_UP_NO_JUMP_ID);
            // Zeroes out jump power entirely, preventing jumping while stunned.
            jumpStrength.addTransientModifier(new AttributeModifier(READY_UP_NO_JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void removeReadyUpAttributes(Player player) {
        AttributeInstance moveSpeed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (moveSpeed != null) moveSpeed.removeModifier(READY_UP_SLOWDOWN_ID);
        AttributeInstance jumpStrength = player.getAttribute(Attributes.JUMP_STRENGTH);
        if (jumpStrength != null) jumpStrength.removeModifier(READY_UP_NO_JUMP_ID);
    }

    private static void sync(Player player, boolean active) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new TerragolemReadyUpStatePayload(player.getUUID(), active));
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Sync all currently readying-up and held players to the newly joined player.
        if (event.getEntity() instanceof ServerPlayer sp) {
            for (UUID uuid : READY_UP_PLAYERS.keySet()) {
                PacketDistributor.sendToPlayer(sp, new TerragolemReadyUpStatePayload(uuid, true));
            }
            for (Map.Entry<UUID, Float> entry : HELD_PLAYERS.entrySet()) {
                PacketDistributor.sendToPlayer(sp, new TerragolemFlareHoldPayload(entry.getKey(), true, entry.getValue()));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (needsLifecycleReset(event.getEntity())) cancel(event.getEntity());
    }
}
