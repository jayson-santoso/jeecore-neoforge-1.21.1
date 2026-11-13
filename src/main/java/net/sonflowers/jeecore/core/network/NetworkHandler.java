package net.sonflowers.jeecore.core.network;

import net.sonflowers.jeecore.gene.dryad.network.DryadElixirTrailPayload;
import net.sonflowers.jeecore.gene.dryad.network.HealPayload;
import net.sonflowers.jeecore.gene.dryad.network.RootSnareVfxPayload;
import net.sonflowers.jeecore.gene.infernal.network.AerialChargeImpactPayload;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeVfxPayload;
import net.sonflowers.jeecore.gene.infernal.network.IgnitionChargeVisualStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.InfernalAerialChargePayload;
import net.sonflowers.jeecore.gene.infernal.network.InfernalPassiveVisualPayload;
import net.sonflowers.jeecore.gene.infernal.network.MoltenGroundVfxPayload;
import net.sonflowers.jeecore.gene.infernal.network.RageEndPayload;
import net.sonflowers.jeecore.gene.infernal.network.RagePayload;
import net.sonflowers.jeecore.gene.infernal.network.RageStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.RageVisualStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.SmokeExitPayload;
import net.sonflowers.jeecore.gene.infernal.network.SmokeFormStatePayload;
import net.sonflowers.jeecore.gene.infernal.network.SmokePayload;
import net.sonflowers.jeecore.gene.nimbus.network.CloudPayload;
import net.sonflowers.jeecore.gene.nimbus.network.DashEffectPayload;
import net.sonflowers.jeecore.gene.nimbus.network.GlideBoostEffectPayload;
import net.sonflowers.jeecore.gene.nimbus.network.GlideStatePayload;
import net.sonflowers.jeecore.gene.nimbus.network.ToggleGlidePayload;
import net.sonflowers.jeecore.gene.nimbus.network.WindPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemBeamBurstPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemBigStunPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemFlareHoldPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemOverheatDebuffPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemOverheatPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemReadyUpClickPayload;
import net.sonflowers.jeecore.gene.terragolem.network.TerragolemReadyUpStatePayload;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Registers shared and gene-specific payload codecs, directions, and handlers on the mod event
 * bus.
 */
public class NetworkHandler {
    // Mod bus registration.
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(NetworkHandler::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(net.sonflowers.jeecore.gene.nimbus.network.WindrunnerVisualPayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerVisualPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerVisualPayload::handle);
        registrar.playToServer(net.sonflowers.jeecore.gene.nimbus.network.WindrunnerAttackPayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerAttackPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerAttackPayload::handle);
        registrar.playToClient(net.sonflowers.jeecore.gene.nimbus.network.NimbusDodgePayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.NimbusDodgePayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.NimbusDodgePayload::handle);
        registrar.playToClient(net.sonflowers.jeecore.gene.nimbus.network.WindrunnerStatePayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerStatePayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.WindrunnerStatePayload::handle);
        registrar.playToServer(net.sonflowers.jeecore.gene.nimbus.network.GustInputPayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.GustInputPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.GustInputPayload::handle);
        registrar.playToClient(net.sonflowers.jeecore.gene.nimbus.network.GustChargePayload.TYPE,
                net.sonflowers.jeecore.gene.nimbus.network.GustChargePayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.nimbus.network.GustChargePayload::handle);
        registrar.playToServer(
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemJumpPayload.TYPE,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemJumpPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemJumpPayload::handle);
        registrar.playToClient(
                net.sonflowers.jeecore.gene.dryad.network.SporeVfxPayload.TYPE,
                net.sonflowers.jeecore.gene.dryad.network.SporeVfxPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.dryad.network.SporeVfxPayload::handle);
        registrar.playToClient(
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload.TYPE,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemMalfunctionPayload::handle);
        registrar.playToClient(
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemResetPayload.TYPE,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemResetPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.terragolem.network.TerragolemResetPayload::handle);
        registrar.playToServer(
                net.sonflowers.jeecore.gene.terragolem.network.OpenTerragolemRepairMenuPayload.TYPE,
                net.sonflowers.jeecore.gene.terragolem.network.OpenTerragolemRepairMenuPayload.STREAM_CODEC,
                net.sonflowers.jeecore.gene.terragolem.network.OpenTerragolemRepairMenuPayload::handle);

        registrar.playToClient(
                BeamPayload.TYPE,
                BeamPayload.STREAM_CODEC,
                BeamPayload::handle
        );

        registrar.playToClient(
                HealPayload.TYPE,
                HealPayload.STREAM_CODEC,
                HealPayload::handle
        );

        registrar.playToClient(
                WindPayload.TYPE,
                WindPayload.STREAM_CODEC,
                WindPayload::handle
        );

        registrar.playToClient(
                RagePayload.TYPE,
                RagePayload.STREAM_CODEC,
                RagePayload::handle
        );

        registrar.playToClient(
                RageEndPayload.TYPE,
                RageEndPayload.STREAM_CODEC,
                RageEndPayload::handle
        );

        registrar.playToClient(
                RageStatePayload.TYPE,
                RageStatePayload.STREAM_CODEC,
                RageStatePayload::handle
        );

        registrar.playToClient(
                RageVisualStatePayload.TYPE,
                RageVisualStatePayload.STREAM_CODEC,
                RageVisualStatePayload::handle
        );

        registrar.playToClient(
                GeneStatePayload.TYPE,
                GeneStatePayload.STREAM_CODEC,
                GeneStatePayload::handle
        );

        registrar.playToClient(
                SmokePayload.TYPE,
                SmokePayload.STREAM_CODEC,
                SmokePayload::handle
        );

        registrar.playToClient(
                SmokeExitPayload.TYPE,
                SmokeExitPayload.STREAM_CODEC,
                SmokeExitPayload::handle
        );

        registrar.playToClient(
                CloudPayload.TYPE,
                CloudPayload.STREAM_CODEC,
                CloudPayload::handle
        );

        registrar.playToServer(
                GeneSelectionPayload.TYPE,
                GeneSelectionPayload.STREAM_CODEC,
                GeneSelectionPayload::handle
        );

        registrar.playToClient(
                OpenGeneScreenPayload.TYPE,
                OpenGeneScreenPayload.STREAM_CODEC,
                OpenGeneScreenPayload::handle
        );

        registrar.playToServer(
                UseAbilityPayload.TYPE,
                UseAbilityPayload.STREAM_CODEC,
                UseAbilityPayload::handle
        );

        registrar.playToClient(
                DashEffectPayload.TYPE,
                DashEffectPayload.STREAM_CODEC,
                DashEffectPayload::handle
        );

        registrar.playToServer(
                ToggleGlidePayload.TYPE,
                ToggleGlidePayload.STREAM_CODEC,
                ToggleGlidePayload::handle
        );

        registrar.playToClient(
                GlideStatePayload.TYPE,
                GlideStatePayload.STREAM_CODEC,
                GlideStatePayload::handle
        );

        registrar.playToClient(
                CooldownStatePayload.TYPE,
                CooldownStatePayload.STREAM_CODEC,
                CooldownStatePayload::handle
        );

        registrar.playToServer(
                RequestCooldownSyncPayload.TYPE,
                RequestCooldownSyncPayload.STREAM_CODEC,
                RequestCooldownSyncPayload::handle
        );

        registrar.playToClient(
                GlideBoostEffectPayload.TYPE,
                GlideBoostEffectPayload.STREAM_CODEC,
                GlideBoostEffectPayload::handle
        );

        registrar.playToClient(
                SmokeFormStatePayload.TYPE,
                SmokeFormStatePayload.STREAM_CODEC,
                SmokeFormStatePayload::handle
        );

        registrar.playToClient(
                IgnitionChargeStatePayload.TYPE,
                IgnitionChargeStatePayload.STREAM_CODEC,
                IgnitionChargeStatePayload::handle
        );

        registrar.playToClient(
                SwingHandPayload.TYPE,
                SwingHandPayload.STREAM_CODEC,
                SwingHandPayload::handle
        );

        registrar.playToClient(
                IgnitionChargeVfxPayload.TYPE,
                IgnitionChargeVfxPayload.STREAM_CODEC,
                IgnitionChargeVfxPayload::handle
        );

        registrar.playToClient(
                IgnitionChargeVisualStatePayload.TYPE,
                IgnitionChargeVisualStatePayload.STREAM_CODEC,
                IgnitionChargeVisualStatePayload::handle
        );

        registrar.playToClient(
                InfernalPassiveVisualPayload.TYPE,
                InfernalPassiveVisualPayload.STREAM_CODEC,
                InfernalPassiveVisualPayload::handle
        );

        registrar.playToClient(
                InfernalAerialChargePayload.TYPE,
                InfernalAerialChargePayload.STREAM_CODEC,
                InfernalAerialChargePayload::handle
        );

        registrar.playToClient(
                MoltenGroundVfxPayload.TYPE,
                MoltenGroundVfxPayload.STREAM_CODEC,
                MoltenGroundVfxPayload::handle
        );

        registrar.playToClient(
                AerialChargeImpactPayload.TYPE,
                AerialChargeImpactPayload.STREAM_CODEC,
                AerialChargeImpactPayload::handle
        );

        registrar.playToClient(
                DryadElixirTrailPayload.TYPE,
                DryadElixirTrailPayload.STREAM_CODEC,
                DryadElixirTrailPayload::handle
        );

        registrar.playToClient(
                RootSnareVfxPayload.TYPE,
                RootSnareVfxPayload.STREAM_CODEC,
                RootSnareVfxPayload::handle
        );

        registrar.playToClient(
                TerragolemReadyUpStatePayload.TYPE,
                TerragolemReadyUpStatePayload.STREAM_CODEC,
                TerragolemReadyUpStatePayload::handle
        );

        registrar.playToServer(
                TerragolemReadyUpClickPayload.TYPE,
                TerragolemReadyUpClickPayload.STREAM_CODEC,
                TerragolemReadyUpClickPayload::handle
        );

        registrar.playToClient(
                TerragolemFlareHoldPayload.TYPE,
                TerragolemFlareHoldPayload.STREAM_CODEC,
                TerragolemFlareHoldPayload::handle
        );

        registrar.playToClient(
                TerragolemBigStunPayload.TYPE,
                TerragolemBigStunPayload.STREAM_CODEC,
                TerragolemBigStunPayload::handle
        );

        registrar.playToClient(
                TerragolemOverheatPayload.TYPE,
                TerragolemOverheatPayload.STREAM_CODEC,
                TerragolemOverheatPayload::handle
        );

        registrar.playToClient(
                TerragolemOverheatDebuffPayload.TYPE,
                TerragolemOverheatDebuffPayload.STREAM_CODEC,
                TerragolemOverheatDebuffPayload::handle
        );

        registrar.playToClient(
                TerragolemBeamBurstPayload.TYPE,
                TerragolemBeamBurstPayload.STREAM_CODEC,
                TerragolemBeamBurstPayload::handle
        );

    }
}
