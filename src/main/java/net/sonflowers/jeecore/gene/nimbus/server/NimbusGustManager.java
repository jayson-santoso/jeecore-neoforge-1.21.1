package net.sonflowers.jeecore.gene.nimbus.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.ability.AbilityHandler;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.gene.nimbus.network.GustChargePayload;
import net.sonflowers.jeecore.gene.nimbus.network.GustInputPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Validates server gust input and manages charge, cancellation, and release.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class NimbusGustManager {
    public static final int CHARGE_TICKS = 24;
    private static final int HOLD_TIMEOUT = 20;
    private static final Map<UUID, Charge> CHARGES = new HashMap<>();
    private static final class Charge {
        final long started;
        final int entityId;
        final ResourceKey<Level> dimension;
        long lastHeld;
        Charge(ServerPlayer player) {
            started = lastHeld = player.level().getGameTime();
            entityId = player.getId();
            dimension = player.level().dimension();
        }
    }
    private NimbusGustManager() {}
    private static boolean valid(ServerPlayer player) {
        return player.isAlive() && !player.hasDisconnected() && !player.isSpectator()
                && GeneHelper.getPlayerGene(player) == Gene.NIMBUS;
    }
    public static void start(ServerPlayer player) {
        if (!valid(player) || CHARGES.containsKey(player.getUUID()) || !AbilityHandler.canUseSecondary(player)) return;
        Charge state = new Charge(player);
        CHARGES.put(player.getUUID(), state);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new GustChargePayload(true, state.started, player.getId()));
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BREEZE_INHALE, SoundSource.PLAYERS, 0.4f, 1.3f);
    }
    public static void handleInput(ServerPlayer player, byte action) {
        switch (action) {
            case GustInputPayload.PRESS -> start(player);
            case GustInputPayload.RELEASE -> release(player);
            case GustInputPayload.CANCEL -> cancel(player);
            case GustInputPayload.HOLD -> {
                Charge state = CHARGES.get(player.getUUID());
                if (state != null) state.lastHeld = player.level().getGameTime();
            }
            default -> { }
        }
    }
    private static void release(ServerPlayer player) {
        Charge state = CHARGES.remove(player.getUUID());
        if (state == null) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new GustChargePayload(false, 0, player.getId()));
        if (!valid(player) || state.entityId != player.getId() || state.dimension != player.level().dimension()) return;
        float charge = Math.max(0, Math.min(1, (player.level().getGameTime() - state.started) / (float) CHARGE_TICKS));
        NimbusAbilities.gust(player, charge);
    }
    public static void cancel(ServerPlayer player) {
        if (CHARGES.remove(player.getUUID()) != null) PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new GustChargePayload(false, 0, player.getId()));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        for (UUID id : CHARGES.keySet().toArray(UUID[]::new)) {
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(id);
            Charge state = CHARGES.get(id);
            if (player == null) { CHARGES.remove(id); continue; }
            if (!valid(player) || state.entityId != player.getId() || state.dimension != player.level().dimension()
                    || player.level().getGameTime() - state.lastHeld > HOLD_TIMEOUT) { cancel(player); continue; }
            if (player.level().getGameTime() - state.started >= CHARGE_TICKS) release(player);
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { CHARGES.remove(event.getEntity().getUUID()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { CHARGES.clear(); }
}

