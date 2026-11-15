package net.sonflowers.jeecore.gene.nimbus.server;

import net.sonflowers.jeecore.core.ability.AbilityHandler;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.gene.nimbus.network.CloudPayload;
import net.sonflowers.jeecore.gene.nimbus.network.WindPayload;

/**
 * Dispatches server Nimbus dash and charged gust abilities.
 */
public class NimbusAbilities {
    public static void dash(Player player) {

        if (player.isFallFlying()) return;

        // Add velocity in the direction player is looking (including vertical).
        Vec3 look = player.getLookAngle();
        double speed = 2.0;
        Vec3 velocity = look.scale(speed);
        player.setDeltaMovement(player.getDeltaMovement().add(velocity));
        player.hurtMarked = true;

        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.GHAST_SHOOT, SoundSource.PLAYERS, 0.6f, 2.0f);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), 
            SoundEvents.TRIDENT_RIPTIDE_3, SoundSource.PLAYERS, 1.0f, 1.4f);

        player.causeFoodExhaustion(2.0f);

        // Enable cloud effect.
        CloudManager.setClouded(player.getUUID(), true);
        if (player instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayersTrackingEntity(sp, new CloudPayload(player.getUUID(), true));
            PacketDistributor.sendToPlayer(sp, new CloudPayload(player.getUUID(), true));
        }

        // Start dash effect (invisibility + invulnerability + overlay).
        DashEffectManager.startDashEffect(player);

        AbilityHandler.setPrimaryCooldown(player);
    }

    public static void gust(Player player, float charge) {
        if (player.level().isClientSide) return;
        WindManager.applyGust(player, charge);
        WindPayload payload = new WindPayload(player.getBoundingBox().getCenter(), player.getLookAngle(), 8.0f, charge, player.getId());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, payload);
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 0.8f + 0.4f * charge, 1.1f - 0.4f * charge);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        AbilityHandler.setSecondaryCooldown(player);
    }
}
