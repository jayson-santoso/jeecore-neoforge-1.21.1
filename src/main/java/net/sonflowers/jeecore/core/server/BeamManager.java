package net.sonflowers.jeecore.core.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.JeeCore;
import net.sonflowers.jeecore.core.network.BeamPayload;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks beam state on both logical sides; the server applies beam hits and synchronizes state to
 * clients.
 */
@EventBusSubscriber(modid = JeeCore.MOD_ID)
public class BeamManager {
    // Tracks beaming state on both client and server.
    private static final Map<UUID, Long> BEAMING_PLAYERS = new ConcurrentHashMap<>();
    // Optional per-player beam range override (server-side hit logic); defaults to 30.
    private static final Map<UUID, Double> BEAM_RANGES = new ConcurrentHashMap<>();

    public static void setBeamRange(Player player, double range) {
        BEAM_RANGES.put(player.getUUID(), range);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide() && isBeaming(player)) {
            applyBeamHit(player);
            applyBeamRecoil(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        // Sync all currently beaming players to the newly joined player.
        if (event.getEntity() instanceof ServerPlayer sp) {
            for (UUID uuid : BEAMING_PLAYERS.keySet()) {
                PacketDistributor.sendToPlayer(sp, new BeamPayload(uuid, true));
            }
        }
    }

    private static void applyBeamRecoil(Player player) {
        if (player.isShiftKeyDown()) return;
        Vec3 look = player.getViewVector(1.0F);
        Vec3 recoilDir = look.reverse();
        player.addDeltaMovement(new Vec3(recoilDir.x * 0.035, recoilDir.y * 0.09, recoilDir.z * 0.035));
        player.hurtMarked = true;
        if (recoilDir.y * 0.09 > 0 || player.getDeltaMovement().y > -0.1) {
            player.fallDistance = 0;
        }
    }

    private static void applyBeamHit(Player player) {
        Vec3 start = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double range = BEAM_RANGES.getOrDefault(player.getUUID(), 30.0);
        Vec3 beamOffset = look.scale(range);
        Vec3 end = start.add(beamOffset);

        Long startTime = BEAMING_PLAYERS.get(player.getUUID());
        long elapsedTicks= player.level().getGameTime() - startTime;

        // Flat ~1 damage for the first 2 seconds, linear ramp to 5 at 6 seconds, then constant.
        float damage;
        if (elapsedTicks <= 40) damage = 1.0F;
        else if (elapsedTicks >= 120) damage = 5.0F;
        else damage = 1.0F + (elapsedTicks - 40) * (4.0F / 80.0F);

        BlockHitResult blockHit = player.level().clip(
                new ClipContext(start, end, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player));
        Vec3 actualEnd = blockHit.getLocation();
        if (GeneHelper.getPlayerGene(player) == Gene.TERRAGOLEM && elapsedTicks % 8 == 0) {
            spawnBlockImpactSmoke(player, blockHit);
        }

        AABB area = player.getBoundingBox().expandTowards(beamOffset).inflate(1.0D);
        for (LivingEntity target : player.level().getEntitiesOfClass(
                LivingEntity.class, area, target -> target != player && target.isPickable())) {
            if (target.getBoundingBox().inflate(0.5D).clip(start, actualEnd).isPresent()) {
                Vec3 pushVector = look.scale(0.04);
                target.setDeltaMovement(target.getDeltaMovement().add(pushVector.x, pushVector.y, pushVector.z));
                target.hurt(player.damageSources().playerAttack(player), damage);
                target.hurtMarked = true;
            }
        }
    }

    public static void spawnBlockImpactSmoke(Player player, BlockHitResult hit) {
        if (!(player.level() instanceof ServerLevel level) || hit.getType() != HitResult.Type.BLOCK) return;
        // Offset outside the impacted face so the slow smoke is visible rather than buried in the
        // block.
        Vec3 point = hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.06));
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, point.x, point.y, point.z,
                4, 0.3, 0.3, 0.3, 0.2);
    }

    public static boolean isBeaming(Player player) {
        return player != null && BEAMING_PLAYERS.containsKey(player.getUUID());
    }

    public static boolean isBeamingUUID(UUID uuid) {
        return BEAMING_PLAYERS.containsKey(uuid);
    }

    public static boolean hasBeamingPlayers() {
        return !BEAMING_PLAYERS.isEmpty();
    }

    /**
     * Server-side: ticks elapsed since the beam started, 0 when not beaming.
     */
    public static long getBeamingElapsedTicks(Player player) {
        Long start = BEAMING_PLAYERS.get(player.getUUID());
        if (start == null) return 0;
        return Math.max(0, player.level().getGameTime() - start);
    }

    public static void setBeaming(Player player, boolean beaming) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        Long startTime = player.level().getGameTime();
        boolean changed = beaming != isBeamingUUID(uuid);
        if (beaming) BEAMING_PLAYERS.put(uuid, startTime);
        else {
            BEAMING_PLAYERS.remove(uuid);
            BEAM_RANGES.remove(uuid);
        }
        if (changed) {
            PacketDistributor.sendToPlayersTrackingEntity(player, new BeamPayload(uuid, beaming));
            if (player instanceof ServerPlayer sp) {
                PacketDistributor.sendToPlayer(sp, new BeamPayload(uuid, beaming));
            }
        }
    }

    /**
     * Called client-side from BeamPayload handler — does not re-broadcast.
     */
    public static void setBeamingClient(UUID uuid, boolean beaming) {
        if (beaming) BEAMING_PLAYERS.put(uuid, 0L);
        else BEAMING_PLAYERS.remove(uuid);
    }
}
