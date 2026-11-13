package net.sonflowers.jeecore.gene.infernal.server;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.gene.PlayerGeneData;
import net.sonflowers.jeecore.gene.infernal.network.SmokeExitPayload;
import net.sonflowers.jeecore.gene.infernal.network.SmokeFormStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.SmokePayload;
import net.sonflowers.jeecore.gene.infernal.server.SmokeManager;

/**
 * Runs server smoke-form toggles, resource consumption, cooldowns, and synchronized client state.
 */
public class InfernalSmokeManager {
    private static final int RECHARGE_INTERVAL_TICKS = 4;
    private static final int STOP_COOLDOWN_TICKS = 60; // Three seconds.
    private static final ResourceLocation SWIM_SPEED_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(JeeCore.MOD_ID, "infernal_smoke_swim_speed");
    private static final double AIR_SWIM_SPEED = 0.15;

    public static void toggleSmokeForm(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;

        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);

        // Cannot use smoke form during rage mode.
        if (InfernalRageManager.isRaging(player)) return;

        if (data.isSmokeFormActive()) {
            setSmokeForm(player, false);
            return;
        }

        if (data.getSmokeResource() <= 0 || data.getSmokeCooldownTicks() > 0
                || InfernalChargeManager.isReady(player)) return;
        setSmokeForm(player, true);
    }

    public static void tick(Player player) {
        if (GeneHelper.getPlayerGene(player) != Gene.INFERNAL) return;

        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        tickStopCooldown(player, data);
        if (data.isSmokeFormActive()) {
            tickActive(player, data);
        } else if (player.level().getGameTime() % RECHARGE_INTERVAL_TICKS == 0) {
            addSmokeResource(player, 1);
        }
    }

    public static boolean isSmokeFormActive(Player player) {
        return player.getData(GeneDataAttachment.PLAYER_GENE_DATA).isSmokeFormActive();
    }

    public static void sync(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        syncSmokeFormState(player, data);
    }

    public static void removeSmokeForm(Player player) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean wasActive = data.isSmokeFormActive();
        data.setSmokeFormActive(false);
        data.setSmokeCooldownTicks(0);
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(MobEffects.LEVITATION);
        removeSwimSpeedModifier(player);
        SmokeManager.setSmoking(player.getUUID(), false);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntity(sp, new SmokePayload(player.getUUID(), false));
            PacketDistributor.sendToPlayer(sp, new SmokePayload(player.getUUID(), false));
        }
        if (wasActive) {
            spawnSmokeExitBurst(player);
        }
        syncSmokeFormState(player, data);
    }

    private static void tickActive(Player player, PlayerGeneData data) {
        if (data.getSmokeResource() <= 0) {
            setSmokeForm(player, false);
            return;
        }

        applySmokeMovement(player);
        applySwimSpeedModifier(player);
        applySmokeFormEffects(player);
        applyTouchEffects(player);
        InfernalPassiveHandler.drainAbilityHunger(player, 0.035f);
        addSmokeResource(player, -1);

        if (data.getSmokeResource() <= 0) {
            setSmokeForm(player, false);
        }
    }

    private static void applySmokeMovement(Player player) {
        player.setSwimming(true);
        if (player.isInWater()) return;

        Vec3 look = player.getLookAngle();
        Vec3 current = player.getDeltaMovement();
        Vec3 push = look.scale(AIR_SWIM_SPEED);
        double y = current.y * 0.72 + push.y * 0.95;
        player.setDeltaMovement(current.x * 0.86 + push.x, y, current.z * 0.86 + push.z);
        player.hurtMarked = true;
    }

    private static void applySwimSpeedModifier(Player player) {
        AttributeInstance swimAttr = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (swimAttr != null && !swimAttr.hasModifier(SWIM_SPEED_MODIFIER_ID)) {
            swimAttr.addTransientModifier(new AttributeModifier(
                    SWIM_SPEED_MODIFIER_ID,
                    0.095,
                    AttributeModifier.Operation.ADD_VALUE
            ));
        }
    }

    private static void removeSwimSpeedModifier(Player player) {
        AttributeInstance swimAttr = player.getAttribute(Attributes.WATER_MOVEMENT_EFFICIENCY);
        if (swimAttr != null) {
            swimAttr.removeModifier(SWIM_SPEED_MODIFIER_ID);
        }
    }

    private static void applySmokeFormEffects(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 6, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, -1, false, false));
    }

    private static void applyTouchEffects(Player player) {
        AABB box = player.getBoundingBox().inflate(1.15);
        for (LivingEntity target : player.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> entity != player && entity.isAlive())) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 25, 2));
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
        }
    }

    private static void setSmokeForm(Player player, boolean active) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        boolean wasActive = data.isSmokeFormActive();
        data.setSmokeFormActive(active);

        if (active) {
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0f, 0.8f);
        } else {
            player.removeEffect(MobEffects.INVISIBILITY);
            player.removeEffect(MobEffects.LEVITATION);
            removeSwimSpeedModifier(player);
            if (wasActive) {
                data.setSmokeCooldownTicks(STOP_COOLDOWN_TICKS);
                spawnSmokeExitBurst(player);
            }
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PUFFER_FISH_BLOW_OUT, SoundSource.PLAYERS, 1.0f, 0.4f);
        }

        SmokeManager.setSmoking(player.getUUID(), active);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntity(sp, new SmokePayload(player.getUUID(), active));
            PacketDistributor.sendToPlayer(sp, new SmokePayload(player.getUUID(), active));
        }
        syncSmokeFormState(player, data);
    }

    private static void addSmokeResource(Player player, int amount) {
        PlayerGeneData data = player.getData(GeneDataAttachment.PLAYER_GENE_DATA);
        int oldResource = data.getSmokeResource();
        data.addSmokeResource(amount);
        if (oldResource != data.getSmokeResource()) {
            syncSmokeFormState(player, data);
        }
    }

    private static void spawnSmokeExitBurst(Player player) {
        // Send to all tracking players and the player themselves.
        if (player instanceof ServerPlayer sp) {
            Vec3 pos = player.position();
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp, new SmokeExitPayload(player.getUUID(), pos));
        }
    }

    private static void tickStopCooldown(Player player, PlayerGeneData data) {
        if (data.getSmokeCooldownTicks() <= 0) return;
        data.setSmokeCooldownTicks(data.getSmokeCooldownTicks() - 1);
        syncSmokeFormState(player, data);
    }

    private static void syncSmokeFormState(Player player, PlayerGeneData data) {
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new SmokeFormStatePayload(
                    data.isSmokeFormActive(),
                    data.getSmokeResource(),
                    data.getSmokeCooldownTicks()
            ));
        }
    }
}
