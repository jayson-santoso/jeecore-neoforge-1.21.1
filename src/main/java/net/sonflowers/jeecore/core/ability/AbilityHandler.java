package net.sonflowers.jeecore.core.ability;

import net.sonflowers.jeecore.gene.dryad.server.DryadElixirManager;
import net.sonflowers.jeecore.gene.dryad.server.DryadPassiveHandler;
import net.sonflowers.jeecore.gene.dryad.server.DryadRootSnareManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalAerialChargeManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalRageManager;
import net.sonflowers.jeecore.gene.infernal.server.InfernalSmokeManager;
import net.sonflowers.jeecore.gene.nimbus.server.NimbusAbilities;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemOverheatManager;
import net.sonflowers.jeecore.gene.terragolem.server.TerragolemReadyUpManager;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.sonflowers.jeecore.core.gene.Gene;
import net.sonflowers.jeecore.core.gene.GeneHelper;
import net.sonflowers.jeecore.core.network.CooldownStatePayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dispatches server-side gene abilities and synchronizes per-player cooldowns in game ticks.
 */
public class AbilityHandler {
    private static final Map<UUID, Long> PRIMARY_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> SECONDARY_COOLDOWNS = new HashMap<>();

    public static boolean canUsePrimary(Player player) {
        long current = player.level().getGameTime();
        Gene gene = GeneHelper.getPlayerGene(player);
        long lastUse = PRIMARY_COOLDOWNS.getOrDefault(player.getUUID(), -(long) gene.getPrimaryCooldown());
        return current - lastUse >= gene.getPrimaryCooldown();
    }

    public static void setPrimaryCooldown(Player player) {
        PRIMARY_COOLDOWNS.put(player.getUUID(), player.level().getGameTime());
        syncCooldown(player, true);
    }

    public static int getPrimaryCooldownRemaining(Player player) {
        long current = player.level().getGameTime();
        Gene gene = GeneHelper.getPlayerGene(player);
        long lastUse = PRIMARY_COOLDOWNS.getOrDefault(player.getUUID(), -(long) gene.getPrimaryCooldown());
        return Math.max(0, (int)(gene.getPrimaryCooldown() - (current - lastUse)));
    }

    public static boolean canUseSecondary(Player player) {
        long current = player.level().getGameTime();
        Gene gene = GeneHelper.getPlayerGene(player);
        long lastUse = SECONDARY_COOLDOWNS.getOrDefault(player.getUUID(), -(long) gene.getSecondaryCooldown());
        return current - lastUse >= gene.getSecondaryCooldown();
    }

    public static void setSecondaryCooldown(Player player) {
        SECONDARY_COOLDOWNS.put(player.getUUID(), player.level().getGameTime());
        syncCooldown(player, false);
    }

    public static int getSecondaryCooldownRemaining(Player player) {
        long current = player.level().getGameTime();
        Gene gene = GeneHelper.getPlayerGene(player);
        long lastUse = SECONDARY_COOLDOWNS.getOrDefault(player.getUUID(), -(long) gene.getSecondaryCooldown());
        return Math.max(0, (int)(gene.getSecondaryCooldown() - (current - lastUse)));
    }

    public static void usePrimaryAbility(Player player) {
        if (!canUsePrimary(player)) return;

        Gene gene = GeneHelper.getPlayerGene(player);
        if (gene == Gene.DRYAD && DryadPassiveHandler.isAbilityBlocked(player)) return;
        // Terragolem primary is disabled while readying up, during the post-release aftermath, and
        // while the overheat debuff lock is active.
        if (gene == Gene.TERRAGOLEM
                && (TerragolemReadyUpManager.isReadyUp(player.getUUID())
                    || TerragolemReadyUpManager.isInAftermath(player.getUUID())
                    || TerragolemOverheatManager.isPrimaryLocked(player.getUUID()))) return;

        switch (gene) {
            case NIMBUS -> NimbusAbilities.dash(player);
            case INFERNAL -> InfernalSmokeManager.toggleSmokeForm(player);
            case DRYAD -> DryadElixirManager.throwElixir(player);
            case TERRAGOLEM -> TerragolemReadyUpManager.activate(player);
            default -> {}
        }
    }

    public static void useSecondaryAbility(Player player) {
        if (!canUseSecondary(player)) return;

        Gene gene = GeneHelper.getPlayerGene(player);
        if (gene == Gene.DRYAD && DryadPassiveHandler.isAbilityBlocked(player)) return;

        switch (gene) {
            case NIMBUS -> {
                if (player instanceof ServerPlayer serverPlayer) net.sonflowers.jeecore.gene.nimbus.server.NimbusGustManager.start(serverPlayer);
            }
            case INFERNAL -> {
                if (!InfernalAerialChargeManager.activateOrDive(player)) {
                    InfernalRageManager.activate(player);
                }
            }
            case DRYAD -> DryadRootSnareManager.activate(player);
            default -> {}
        }
    }

    private static void syncCooldown(Player player, boolean primary) {
        Gene gene = GeneHelper.getPlayerGene(player);
        int maxTicks = primary ? gene.getPrimaryCooldown() : gene.getSecondaryCooldown();
        int remainingTicks = primary ? getPrimaryCooldownRemaining(player) : getSecondaryCooldownRemaining(player);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new CooldownStatePayload(primary, remainingTicks, maxTicks));
        }
    }

    public static void syncCooldowns(Player player) {
        syncCooldown(player, true);
        syncCooldown(player, false);
    }
}
