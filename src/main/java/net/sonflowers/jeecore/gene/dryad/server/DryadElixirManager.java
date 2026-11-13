package net.sonflowers.jeecore.gene.dryad.server;

import net.sonflowers.jeecore.core.ability.AbilityHandler;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.dryad.network.DryadElixirTrailPayload;
import net.sonflowers.jeecore.gene.dryad.server.HealManager;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Runs server elixir throws and ticks their gameplay alongside root snare updates.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class DryadElixirManager {
    public static final String ELIXIR_TAG = "jeecore_dryad_elixir";
    private static final int AURA_DURATION_TICKS = 200;
    private static final int HEAL_INTERVAL_TICKS = 5;
    private static final float HEAL_AMOUNT = 1.0f;
    private static final double AURA_RADIUS = 3.5;
    private static final double AURA_RADIUS_SQR = AURA_RADIUS * AURA_RADIUS;
    private static final int GROUNDED_FUSE_TICKS = 10;
    private static final double GRAVITY = 0.095;
    private static final List<AuraZone> ACTIVE_AURAS = new ArrayList<>();
    private static final List<ElixirProjectile> ACTIVE_PROJECTILES = new ArrayList<>();

    public static void throwElixir(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (GeneHelper.getPlayerGene(player) != Gene.DRYAD) return;

        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, serverPlayer.level());
        Vec3 start = player.getEyePosition().add(player.getViewVector(1.0f).scale(0.45)).add(0.0, -0.15, 0.0);
        display.setPos(start.x - 0.18, start.y - 0.18, start.z - 0.18);
        display.setNoGravity(true);
        CustomData.of(createDisplayTag()).loadInto(display);
        display.addTag(ELIXIR_TAG);

        Vec3 look = player.getViewVector(1.0f);
        Vec3 velocity = look.scale(0.72).add(0.0, 0.16, 0.0);
        ACTIVE_PROJECTILES.add(new ElixirProjectile(display, velocity));

        serverPlayer.level().addFreshEntity(display);
        PacketDistributor.sendToPlayersTrackingEntity(display, new DryadElixirTrailPayload(display.getId()));
        PacketDistributor.sendToPlayer(serverPlayer, new DryadElixirTrailPayload(display.getId()));
        serverPlayer.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7f, 2.0f);
        player.swing(InteractionHand.MAIN_HAND, true);
        AbilityHandler.setPrimaryCooldown(player);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Tick root snare for all server levels.
        for (ServerLevel level : event.getServer().getAllLevels()) {
            DryadRootSnareManager.tick(level);
            tickAuras(level);
        }

        Iterator<ElixirProjectile> iterator = ACTIVE_PROJECTILES.iterator();
        while (iterator.hasNext()) {
            ElixirProjectile projectile = iterator.next();
            if (!(projectile.display.level() instanceof ServerLevel level) || !projectile.display.isAlive()) {
                iterator.remove();
                continue;
            }

            if (projectile.grounded) {
                projectile.fuseTicks++;
                if (projectile.fuseTicks >= GROUNDED_FUSE_TICKS) {
                    createAura(level, projectile.landingPos);
                    projectile.display.discard();
                    iterator.remove();
                }
                continue;
            }

            Vec3 current = projectile.display.position().add(0.18, 0.18, 0.18);
            Vec3 next = current.add(projectile.velocity);
            projectile.ageTicks++;
            if (projectile.ageTicks > 80 || next.y < level.getMinBuildHeight()) {
                createAura(level, BlockPos.containing(current));
                projectile.display.discard();
                iterator.remove();
                continue;
            }

            BlockHitResult hit = level.clip(new ClipContext(current, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, projectile.display));

            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                BlockPos landing = hit.getBlockPos().relative(hit.getDirection());
                projectile.display.setPos(hit.getLocation().x - 0.18, hit.getLocation().y + 0.02, hit.getLocation().z - 0.18);
                level.playSound(null, hit.getBlockPos(), SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.55f, 1.65f);
                projectile.velocity = Vec3.ZERO;
                projectile.grounded = true;
                projectile.landingPos = landing;
            } else {
                projectile.display.setPos(next.x - 0.18, next.y - 0.18, next.z - 0.18);
                projectile.velocity = projectile.velocity.add(0.0, -GRAVITY, 0.0).scale(0.985);
            }
        }
    }

    public static void tick(Player player) {
    }

    private static void tickAuras(ServerLevel level) {
        long gameTime = level.getGameTime();

        Iterator<AuraZone> iterator = ACTIVE_AURAS.iterator();
        while (iterator.hasNext()) {
            AuraZone aura = iterator.next();
            if (aura.level != level.dimension()) continue;

            if (gameTime - aura.spawnTime >= AURA_DURATION_TICKS) {
                HealAuraLightManager.release(level, aura.lightPos);
                HealManager.removeZone(aura.pos);
                iterator.remove();
                continue;
            }

            for (ServerPlayer player : level.players()) {
                applyAuraEffects(player, aura, gameTime);
            }
        }
    }

    private static void createAura(ServerLevel level, BlockPos pos) {
        if (ACTIVE_AURAS.removeIf(aura -> {
            if (aura.level != level.dimension() || !aura.pos.equals(pos)) return false;
            HealAuraLightManager.release(level, aura.lightPos);
            return true;
        })) {
            HealManager.removeZone(pos);
        }
        ACTIVE_AURAS.add(new AuraZone(level.dimension(), pos, level.getGameTime(), HealAuraLightManager.acquire(level, pos)));
        HealManager.addZone(pos, level.getGameTime());

        level.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
                12, 0.15, 0.05, 0.15, 0.5);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                24, 0.2, 0.1, 0.2, 1.0);
        level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8f, 1.5f);
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.8f);
    }

    private static void applyAuraEffects(ServerPlayer player, AuraZone aura, long gameTime) {
        Vec3 center = Vec3.atCenterOf(aura.pos);
        double dx = player.getX() - center.x;
        double dz = player.getZ() - center.z;
        if (dx * dx + dz * dz > AURA_RADIUS_SQR || Math.abs(player.getY() - center.y) > 2.5) return;

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 0, true, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 15, 0, true, false, true));

        if (gameTime % HEAL_INTERVAL_TICKS == 0) {
            boolean healed = player.getHealth() < player.getMaxHealth();
            if (healed) {
                player.heal(HEAL_AMOUNT);
                player.serverLevel().sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.0, player.getZ(),
                        5, 0.4, 0.5, 0.4, 0.08);
            }
            player.removeEffect(MobEffects.POISON);
            player.removeEffect(MobEffects.WITHER);
        }
    }

    private static CompoundTag createDisplayTag() {
        CompoundTag tag = new CompoundTag();
        CompoundTag blockState = new CompoundTag();
        blockState.putString("Name", "minecraft:yellow_shulker_box");
        tag.put("block_state", blockState);
        tag.putFloat("view_range", 1.0f);
        tag.putFloat("width", 0.4f);
        tag.putFloat("height", 0.4f);
        tag.putInt("teleport_duration", 2);
        tag.putInt("brightness", 15728880);

        CompoundTag transformation = new CompoundTag();
        ListTag scale = new ListTag();
        scale.add(net.minecraft.nbt.FloatTag.valueOf(0.36f));
        scale.add(net.minecraft.nbt.FloatTag.valueOf(0.36f));
        scale.add(net.minecraft.nbt.FloatTag.valueOf(0.36f));
        transformation.put("scale", scale);
        tag.put("transformation", transformation);
        return tag;
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_AURAS.clear();
        ACTIVE_PROJECTILES.clear();
        HealManager.getZones().clear();
    }

    private record AuraZone(ResourceKey<Level> level, BlockPos pos, long spawnTime, BlockPos lightPos) {
    }

    private static class ElixirProjectile {
        final Display.BlockDisplay display;
        Vec3 velocity;
        boolean grounded;
        int fuseTicks;
        int ageTicks;
        BlockPos landingPos;

        ElixirProjectile(Display.BlockDisplay display, Vec3 velocity) {
            this.display = display;
            this.velocity = velocity;
            this.landingPos = display.blockPosition();
        }
    }
}
