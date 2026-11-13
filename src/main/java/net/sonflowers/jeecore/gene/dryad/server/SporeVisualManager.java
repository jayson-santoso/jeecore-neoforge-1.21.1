package net.sonflowers.jeecore.gene.dryad.server;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.registry.ModMobEffects;
import net.sonflowers.jeecore.gene.dryad.network.SporeVfxPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

/**
 * Synchronizes server spore visual events and guard state, including players who start tracking
 * later.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public final class SporeVisualManager {
    private record Guard(ResourceKey<Level> dimension, long expiry) {}
    private static final Map<UUID, Guard> GUARDS = new HashMap<>();

    private SporeVisualManager() {}

    public static void release(ServerPlayer player) {
        broadcast(player, new SporeVfxPayload(player.getUUID(), player.position(), 0, SporeVfxPayload.RELEASE));
    }

    public static void syncGuard(ServerPlayer player) {
        var effect = player.getEffect(ModMobEffects.SPORE_GUARD);
        Guard previous = GUARDS.get(player.getUUID());
        if (effect == null || !player.isAlive()) {
            if (previous != null) {
                GUARDS.remove(player.getUUID());
                broadcast(player, new SporeVfxPayload(player.getUUID(), player.position(), 0, SporeVfxPayload.END));
            }
            return;
        }
        int duration = effect.isInfiniteDuration() ? Integer.MAX_VALUE : effect.getDuration();
        long expiry = effect.isInfiniteDuration() ? Long.MAX_VALUE
                : player.getServer().overworld().getGameTime() + duration;
        if (previous != null && previous.dimension == player.level().dimension()
                && Math.abs(previous.expiry - expiry) <= 2) return;
        GUARDS.put(player.getUUID(), new Guard(player.level().dimension(), expiry));
        broadcast(player, new SporeVfxPayload(player.getUUID(), player.position(), duration, SporeVfxPayload.GUARD));
    }

    private static void broadcast(ServerPlayer player, SporeVfxPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        var online = new HashSet<UUID>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            syncGuard(player);
        }
        GUARDS.keySet().removeIf(id -> !online.contains(id));
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)
                || !(event.getTarget() instanceof ServerPlayer target)) return;
        var effect = target.getEffect(ModMobEffects.SPORE_GUARD);
        if (effect != null) {
            int duration = effect.isInfiniteDuration() ? Integer.MAX_VALUE : effect.getDuration();
            PacketDistributor.sendToPlayer(viewer, new SporeVfxPayload(
                    target.getUUID(), target.position(), duration, SporeVfxPayload.GUARD));
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GUARDS.clear();
    }
}
