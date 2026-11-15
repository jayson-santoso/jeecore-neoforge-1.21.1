package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerMomentum.Movement;
import net.sonflowers.jeecore.gene.nimbus.network.NimbusDodgePayload;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneDataAttachment;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.nimbus.WindrunnerState;
import net.sonflowers.jeecore.gene.nimbus.network.WindrunnerStatePayload;
import net.sonflowers.jeecore.gene.nimbus.network.WindrunnerVisualPayload;

/**
 * Updates server momentum, dodge, and Nimble state and synchronizes owner HUD and observer
 * visuals.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class NimbusWindrunnerManager {
    private NimbusWindrunnerManager() {}

    public static void tick(Player player) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!player.isAlive()) { clear(player); return; }
        WindrunnerState state = player.getData(GeneDataAttachment.WINDRUNNER);
        long now = player.level().getGameTime();
        Vec3 position = player.position();
        if (state.dimension != null && state.dimension != player.level().dimension()
                || state.previous != null && position.distanceToSqr(state.previous) > 64) {
            clear(player);
            state = player.getData(GeneDataAttachment.WINDRUNNER);
        }
        int oldStage = state.rules.stage();
        boolean first = !state.initialized;
        if (state.previous != null) {
            double distance = position.subtract(state.previous).horizontalDistance();
            Movement movement = excluded(player) ? Movement.EXCLUDED
                    : player.isShiftKeyDown() ? Movement.SNEAK
                    : distance < .03 ? Movement.IDLE
                    : player.isSprinting() ? Movement.SPRINT : Movement.WALK;
            state.rules.tick(now, movement);
        }
        int stage = state.rules.stage();
        updateEffect(player, stage);
        if (stage != oldStage) feedback(sp, stage, stage > oldStage);
        if (first || stage != oldStage || player.tickCount % 5 == 0) {
            PacketDistributor.sendToPlayer(sp, new WindrunnerStatePayload(state.rules.momentum(), stage,
                    state.rules.decaying(), first ? 0 : Integer.compare(stage, oldStage)));
        }
        if (first) syncVisual(sp, stage, false);
        state.initialized = true;
        state.previous = position;
        state.dimension = player.level().dimension();

    }

    private static boolean excluded(Player p) {
        return p.isFallFlying() || p.isInWater() || p.isInLava() || p.onClimbable()
                || p.isPassenger() || p.getAbilities().flying || p.isSpectator()
                || DashEffectManager.isDashing(p.getUUID());
    }

    private static void updateEffect(Player p, int stage) {
        var effect = p.getEffect(ModMobEffects.NIMBLE);
        if (stage == 0) {
            if (effect != null) p.removeEffect(ModMobEffects.NIMBLE);
        } else if (effect == null || effect.getAmplifier() != stage - 1 || effect.getDuration() < 30) {
            // Remove the old amplifier first: vanilla otherwise queues a weaker effect underneath
            // it.
            if (effect != null && effect.getAmplifier() != stage - 1) p.removeEffect(ModMobEffects.NIMBLE);
            p.addEffect(new MobEffectInstance(ModMobEffects.NIMBLE, 60, stage - 1, true, false, true));
        }
    }

    private static void feedback(ServerPlayer p, int stage, boolean promotion) {
        p.serverLevel().playSound(null, p.blockPosition(), promotion ? SoundEvents.BREEZE_JUMP : SoundEvents.BREEZE_LAND,
                SoundSource.PLAYERS, .5f, promotion ? 1 + stage * .18f : .75f);
        if (promotion) p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.PLAYERS, .45f, 1 + stage * .2f);
        syncVisual(p, stage, promotion);
    }

    private static WindrunnerVisualPayload visual(ServerPlayer p, int stage, boolean promotion) {
        var state = p.getData(GeneDataAttachment.WINDRUNNER);
        Vec3 motion = state.previous == null ? p.getDeltaMovement() : p.position().subtract(state.previous);
        motion = motion.multiply(1, 0, 1);
        if (motion.lengthSqr() < .001) motion = p.getLookAngle().multiply(1, 0, 1);
        return new WindrunnerVisualPayload(p.getUUID(), p.position(), motion, stage, promotion);
    }

    private static void syncVisual(ServerPlayer p, int stage, boolean promotion) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, visual(p, stage, promotion));
    }

    public static void clear(Player p) {
        if (p.hasData(GeneDataAttachment.WINDRUNNER)
                && p.getData(GeneDataAttachment.WINDRUNNER).rules.dodging(p.level().getGameTime())) {
            if (p instanceof ServerPlayer sp) PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                    new NimbusDodgePayload(p.getUUID(), p.position(), Vec3.ZERO, p.getYRot(), false));
        }
        p.setData(GeneDataAttachment.WINDRUNNER, new WindrunnerState());
        p.removeEffect(ModMobEffects.NIMBLE);
        if (p instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new WindrunnerStatePayload(0, 0, false, 0));
            syncVisual(sp, 0, false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void dodge(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.isAlive() || event.getAmount() <= 0
                || GeneHelper.getPlayerGene(p) != Gene.NIMBUS || p.isSpectator() || p.isCreative()
                || DashEffectManager.isDashing(p.getUUID())
                || !(event.getSource().getEntity() instanceof LivingEntity attacker) || attacker == p) return;
        WindrunnerState state = p.getData(GeneDataAttachment.WINDRUNNER);
        long now = p.level().getGameTime();
        int before = state.rules.stage();
        boolean protectedHit = state.rules.dodging(now);
        if (!protectedHit && !state.rules.tryDodge(now)) return;
        event.setCanceled(true);
        // Every blocked explosion needs knockback suppression, including free hits inside the window.
        state.lastDodgedSource = event.getSource();
        state.lastDodgeTick = now;
        // Do not spend another charge, replay effects, or extend protection for follow-up hits.
        if (protectedHit) return;
        int after = state.rules.stage();
        updateEffect(p, after);
        feedback(p, after, false);
        PacketDistributor.sendToPlayer(p, new WindrunnerStatePayload(state.rules.momentum(), after,
                state.rules.decaying(), Integer.compare(after, before)));
        Vec3 motion = p.getDeltaMovement().multiply(1, 0, 1);
        if (motion.lengthSqr() < .001) motion = p.getLookAngle().multiply(1, 0, 1).scale(.15);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p,
                new NimbusDodgePayload(p.getUUID(), p.position(), motion, p.getYRot(), true));
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_DEFLECT,
                SoundSource.PLAYERS, .75f, 1.25f);
        p.serverLevel().playSound(null, p.blockPosition(), SoundEvents.BREEZE_SLIDE,
                SoundSource.PLAYERS, .45f, 1.6f);

    }

    @SubscribeEvent public static void explosionKnockback(ExplosionKnockbackEvent event) {
        if (event.getAffectedEntity() instanceof ServerPlayer p && p.hasData(GeneDataAttachment.WINDRUNNER)) {
            var state = p.getData(GeneDataAttachment.WINDRUNNER);
            if (state.lastDodgeTick == p.level().getGameTime()
                    && state.lastDodgedSource != null
                    && state.lastDodgedSource.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)
                    && state.lastDodgedSource.getDirectEntity() == event.getExplosion().getDirectSourceEntity()) {
                event.setKnockbackVelocity(Vec3.ZERO);
                state.lastDodgedSource = null;
            }
        }
    }

    private static void attack(Player player) {
        if (!(player instanceof ServerPlayer p) || GeneHelper.getPlayerGene(p) != Gene.NIMBUS) return;
        var state = p.getData(GeneDataAttachment.WINDRUNNER);
        int before = state.rules.stage();
        state.rules.attack(p.level().getGameTime());
        int after = state.rules.stage();
        updateEffect(p, after);
        if (after < before) feedback(p, after, false);
        PacketDistributor.sendToPlayer(p, new WindrunnerStatePayload(state.rules.momentum(), after,
                state.rules.decaying(), Integer.compare(after, before)));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void landedHit(LivingDamageEvent.Post event) {
        if (event.getNewDamage() > 0 && event.getSource().getEntity() instanceof ServerPlayer attacker
                && attacker != event.getEntity() && !event.getSource().is(net.minecraft.world.damagesource.DamageTypes.THORNS)) {
            attack(attacker);
        }
    }

    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer target
                && GeneHelper.getPlayerGene(target) == Gene.NIMBUS) {
            PacketDistributor.sendToPlayer(observer, visual(target,
                    target.getData(GeneDataAttachment.WINDRUNNER).rules.stage(), false));
        }
    }

    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { clear(event.getEntity()); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { clear(event.getEntity()); }
}
