package net.sonflowers.jeecore.core.server;

import net.sonflowers.jeecore.core.network.GeneStatePayload;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeVfxPayload;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.core.ability.AbilityHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalAerialChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalPassiveHandler;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalSmokeManager;
import net.sonflowers.jeecore.gene.infernal.server.RageManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusGlideManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusPassiveHandler;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemPassiveHandler;
import net.sonflowers.jeecore.core.network.OpenGeneScreenPayload;

/**
 * Coordinates gene lifecycle synchronization, damage rules, and cross-gene player events.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class GeneEventHandler {
    private static final float DRYAD_DAMAGE_DEALT_PENALTY = 1.5f;
    private static final float DRYAD_FIRE_DAMAGE_MULTIPLIER = 1.5f;
    private static final float DRYAD_THORN_DAMAGE = 3.0f;
    private static final float DRYAD_THORN_CHANCE = 0.2f;

    // Player lifecycle and synchronization.
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.getEntity().level().isClientSide
                && GeneHelper.getPlayerGene(event.getOriginal()) == Gene.TERRAGOLEM) {
            net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager.cancel(event.getOriginal());
            net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager.cancel(event.getEntity());
        }
        if (!event.isWasDeath()) {
            event.getEntity().getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA).deserializeNBT(
                    event.getEntity().registryAccess(), event.getOriginal()
                            .getData(GeneDataAttachment.TERRAGOLEM_FUEL_DATA)
                            .serializeNBT(event.getOriginal().registryAccess()));
        }
        PlayerGeneData originalData = event.getOriginal().getData(GeneDataAttachment.PLAYER_GENE_DATA);
        PlayerGeneData newData = event.getEntity().getData(GeneDataAttachment.PLAYER_GENE_DATA);
        newData.copyFrom(originalData);
        if (event.isWasDeath() && newData.getGene() == Gene.TERRAGOLEM) {
            net.sonflowers.jeecore.gene.terragolem.server.TerragolemOverheatManager.clearHeat(event.getEntity());
        }
        newData.setGlideToggled(false);
        newData.setSmokeFormActive(false);
        newData.setIgnitionCharge(0);
        newData.setIgnitionTimer(0);
        newData.setIgnitionQuickTimeActive(false);
        if (event.isWasDeath()) {
            newData.setRageBar(0);
            RageManager.endRage(event.getEntity().getUUID());
            InfernalAerialChargeManager.clearPounceCharges(event.getEntity());
        }
        if (event.getEntity() instanceof ServerPlayer player) {
            applyGeneState(player);
            // On respawn, delay sync more to ensure client is ready.
            scheduleGeneAndCooldownSync(player, 10); // Sync after 10 ticks (0.5s).
            scheduleGeneAndCooldownSync(player, 20); // Sync again after 20 ticks (1.0s) as backup.
        }
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!GeneHelper.hasChosen(player)) {
                PacketDistributor.sendToPlayer(player, new OpenGeneScreenPayload());
            } else {
                applyGeneState(player);
                // Sync gene state to client first, then cooldowns.
                Gene gene = GeneHelper.getPlayerGene(player);
                PacketDistributor.sendToPlayer(player, new net.sonflowers.jeecore.core.network.GeneStatePayload(gene.getSerializedName()));
                AbilityHandler.syncCooldowns(player);
                scheduleDelayedSync(player, 10); // Sync again after 10 ticks (0.5s).
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            applyGeneState(player);
            // Sync gene state to client first, then cooldowns.
            Gene gene = GeneHelper.getPlayerGene(player);
            PacketDistributor.sendToPlayer(player, new net.sonflowers.jeecore.core.network.GeneStatePayload(gene.getSerializedName()));
            AbilityHandler.syncCooldowns(player);
            scheduleDelayedSync(player, 5); // Sync again after 5 ticks.
        }
    }

    // Schedule a delayed gene and cooldown sync (for respawn where client needs more time).
    private static void scheduleGeneAndCooldownSync(ServerPlayer player, int delayTicks) {
        player.getServer().tell(new net.minecraft.server.TickTask(
            player.getServer().getTickCount() + delayTicks,
            () -> {
                if (player.isAlive() && player.hasDisconnected() == false) {
                    Gene gene = GeneHelper.getPlayerGene(player);
                    PacketDistributor.sendToPlayer(player, new net.sonflowers.jeecore.core.network.GeneStatePayload(gene.getSerializedName()));
                    AbilityHandler.syncCooldowns(player);
                }
            }
        ));
    }

    // Schedule a delayed cooldown sync.
    private static void scheduleDelayedSync(ServerPlayer player, int delayTicks) {
        player.getServer().tell(new net.minecraft.server.TickTask(
            player.getServer().getTickCount() + delayTicks,
            () -> {
                if (player.isAlive() && player.hasDisconnected() == false) {
                    AbilityHandler.syncCooldowns(player);
                }
            }
        ));
    }

    private static void applyGeneState(ServerPlayer player) {
        Gene gene = GeneHelper.getPlayerGene(player);
        if (gene == Gene.NIMBUS) {
            NimbusPassiveHandler.applyPassiveAttributes(player);
            NimbusGlideManager.applyGlideAttribute(player);
            NimbusGlideManager.setGlideResource(player, NimbusGlideManager.getGlideResource(player));
        } else if (gene == Gene.INFERNAL) {
            InfernalPassiveHandler.applyPassiveAttributes(player);
            InfernalSmokeManager.removeSmokeForm(player);
            InfernalSmokeManager.sync(player);
            InfernalChargeManager.sync(player);
        } else if (gene == Gene.TERRAGOLEM) {
            TerragolemPassiveHandler.applyPassiveAttributes(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide) return;
        if (event.getSource().getEntity() instanceof Player attacker && InfernalSmokeManager.isSmokeFormActive(attacker)) {
            event.setCanceled(true);
            return;
        }

        if (event.getSource().getEntity() instanceof Player attacker
                && GeneHelper.getPlayerGene(attacker) == Gene.DRYAD
                && !event.getSource().is(DamageTypes.THORNS)) {
            event.setAmount(Math.max(0.0f, event.getAmount() - DRYAD_DAMAGE_DEALT_PENALTY));
        }

        if (event.getSource().getEntity() instanceof Player attacker
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity target
                && InfernalChargeManager.isReady(attacker)) {
            event.setAmount(event.getAmount() * 1.6f);
            InfernalChargeManager.applyChargedHit(attacker, target);
        }

        // Infernal attacker gains rage from dealing damage.
        if (event.getSource().getEntity() instanceof Player attacker
                && GeneHelper.getPlayerGene(attacker) == Gene.INFERNAL
                && event.getEntity() instanceof net.minecraft.world.entity.LivingEntity) {
            InfernalRageManager.onDealDamage(attacker, event.getAmount());
        }

        if (!(event.getEntity() instanceof Player player)) return;

        // Infernal players are immune to fire, lava, campfire, magma block, and explosion damage.
        if (GeneHelper.getPlayerGene(player) == Gene.INFERNAL) {
            if (isFireDamage(event) ||
                event.getSource().is(DamageTypes.EXPLOSION) ||
                event.getSource().is(DamageTypes.PLAYER_EXPLOSION) ||
                event.getSource().is(DamageTypes.FIREWORKS)) {
                event.setCanceled(true);
                return;
            }
            // Accumulate rage from taking damage (from living entities).
            if (event.getSource().getEntity() instanceof net.minecraft.world.entity.LivingEntity) {
                InfernalRageManager.onTakeDamage(player, event.getAmount());
            }
            // Reduce damage during rage mode.
            if (InfernalRageManager.isRaging(player)) {
                event.setAmount(event.getAmount() * 0.5f);
            }
        }

        if (GeneHelper.getPlayerGene(player) == Gene.DRYAD) {
            if (isFireDamage(event)) {
                event.setAmount(event.getAmount() * DRYAD_FIRE_DAMAGE_MULTIPLIER);
            }

            if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
                applyDryadThorns(player, attacker);
            }
        }

        if (GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM) {
            if (event.getSource().is(DamageTypes.DROWN)) {
                event.setCanceled(true);
                return;
            }
            event.setAmount(event.getAmount() * TerragolemPassiveHandler.INCOMING_DAMAGE_MULTIPLIER);
        }

        // Nimbus players are immune to fall and velocity crash damage.
        if (GeneHelper.getPlayerGene(player) == Gene.NIMBUS) {
            if (event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL)) {
                event.setCanceled(true);
            }
        }

        if (InfernalSmokeManager.isSmokeFormActive(player)) {
            event.setCanceled(true);
        }
    }

    private static boolean isFireDamage(LivingIncomingDamageEvent event) {
        return event.getSource().is(DamageTypes.IN_FIRE)
                || event.getSource().is(DamageTypes.LAVA)
                || event.getSource().is(DamageTypes.ON_FIRE)
                || event.getSource().is(DamageTypes.CAMPFIRE)
                || event.getSource().is(DamageTypes.HOT_FLOOR)
                || event.getSource().is(DamageTypes.FIREBALL)
                || event.getSource().is(DamageTypes.UNATTRIBUTED_FIREBALL);
    }

    private static void applyDryadThorns(Player player, LivingEntity attacker) {
        if (player.getRandom().nextFloat() >= DRYAD_THORN_CHANCE) return;

        attacker.hurt(player.damageSources().thorns(player), DRYAD_THORN_DAMAGE);
        player.level().playSound(null, attacker.blockPosition(), SoundEvents.THORNS_HIT,
                SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @SubscribeEvent
    public static void onLivingKnockback(LivingKnockBackEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // Nimbus players take 100% more knockback (double knockback).
        if (GeneHelper.getPlayerGene(player) == Gene.NIMBUS) {
            // Add vertical displacement for more noticeable knockback.
            Vec3 currentVelocity = player.getDeltaMovement();
            player.setDeltaMovement(currentVelocity.x, currentVelocity.y + 0.1, currentVelocity.z);
            player.hurtMarked = true;

            double multiplier = 3.0; // Double knockback.
            event.setStrength((float)(event.getStrength() * multiplier));

        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();

        if (InfernalSmokeManager.isSmokeFormActive(player)) {
            event.setCanceled(true);
            return;
        }

        // Prevent firework usage when Nimbus is gliding.
        if (GeneHelper.getPlayerGene(player) == Gene.NIMBUS) {
            if (player.isFallFlying() && NimbusGlideManager.isGlideToggled(player)) {
                if (event.getItemStack().is(Items.FIREWORK_ROCKET)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (InfernalSmokeManager.isSmokeFormActive(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;
        if (!(event.getTarget() instanceof net.minecraft.world.entity.LivingEntity target)) return;

        // During rage: set target on fire and chance to explode.
        if (InfernalRageManager.isRaging(player)) {
            target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), 80));
            if (player.getRandom().nextFloat() < 0.45f) {
                if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    // Reuse ignition charge explosion renderer.
                    Vec3 targetPos = target.position().add(0, target.getBbHeight() * 0.5, 0);
                    IgnitionChargeVfxPayload payload = new IgnitionChargeVfxPayload(
                            target.getId(), targetPos, true, false);
                    PacketDistributor.sendToPlayersTrackingEntityAndSelf(target, payload);

                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,
                            target.getX(), target.getY() + 1.0, target.getZ(), 8, 0.5, 0.5, 0.5, 0.3);
                    serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,
                            target.getX(), target.getY() + 1.0, target.getZ(), 2, 0.4, 0.5, 0.4, 1.0);
                    serverLevel.explode(null, target.getX(), target.getY(), target.getZ(),
                            1.0f, false, net.minecraft.world.level.Level.ExplosionInteraction.NONE);
                }
                target.setDeltaMovement(target.getDeltaMovement().add(0.0, 0.06, 0.0));
                target.hurtMarked = true;
                player.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                        net.minecraft.sounds.SoundEvents.DRAGON_FIREBALL_EXPLODE,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 2.0f);
            }
        }
    }

}
