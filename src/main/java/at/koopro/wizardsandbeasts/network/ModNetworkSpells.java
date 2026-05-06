package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkSpells {

    private ModNetworkSpells() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                SpellCastC2SPacket.TYPE,
                SpellCastC2SPacket.STREAM_CODEC,
                SpellCastC2SPacket::handle);

        registrar.playToServer(
                SpellSelectC2SPacket.TYPE,
                SpellSelectC2SPacket.STREAM_CODEC,
                SpellSelectC2SPacket::handle);

        registrar.playToServer(
                SpellAssignC2SPacket.TYPE,
                SpellAssignC2SPacket.STREAM_CODEC,
                SpellAssignC2SPacket::handle);

        registrar.playToServer(
                SpellLeviosaAdjustC2SPacket.TYPE,
                SpellLeviosaAdjustC2SPacket.STREAM_CODEC,
                SpellLeviosaAdjustC2SPacket::handle);

        registrar.playToServer(
                ObscurialAbilityUseC2SPacket.TYPE,
                ObscurialAbilityUseC2SPacket.STREAM_CODEC,
                ObscurialAbilityUseC2SPacket::handle);

        registrar.playToClient(
                SpellDataSyncS2CPacket.TYPE,
                SpellDataSyncS2CPacket.STREAM_CODEC,
                SpellDataSyncS2CPacket::handleClient);

        registrar.playToClient(
                SpellDataDeltaS2CPacket.TYPE,
                SpellDataDeltaS2CPacket.STREAM_CODEC,
                SpellDataDeltaS2CPacket::handleClient);
        registrar.playToClient(
                SpellProficiencySyncS2CPacket.TYPE,
                SpellProficiencySyncS2CPacket.STREAM_CODEC,
                SpellProficiencySyncS2CPacket::handleClient);

        registrar.playToClient(
                AvadaBlastS2CPacket.TYPE,
                AvadaBlastS2CPacket.STREAM_CODEC,
                AvadaBlastS2CPacket::handleClient);

        registrar.playToClient(
                SpellImpactBurstS2CPacket.TYPE,
                SpellImpactBurstS2CPacket.STREAM_CODEC,
                SpellImpactBurstS2CPacket::handleClient);
        registrar.playToClient(
                ProtegoSpawnS2CPacket.TYPE,
                ProtegoSpawnS2CPacket.STREAM_CODEC,
                ProtegoSpawnS2CPacket::handleClient);
        registrar.playToClient(
                ProtegoAnimationS2CPacket.TYPE,
                ProtegoAnimationS2CPacket.STREAM_CODEC,
                ProtegoAnimationS2CPacket::handleClient);

        registrar.playToClient(
                PatronusFormSetS2CPacket.TYPE,
                PatronusFormSetS2CPacket.STREAM_CODEC,
                PatronusFormSetS2CPacket::handleClient);
        registrar.playToClient(
                ImperioControlS2CPacket.TYPE,
                ImperioControlS2CPacket.STREAM_CODEC,
                ImperioControlS2CPacket::handleClient);
        registrar.playToClient(
                ImperioResistS2CPacket.TYPE,
                ImperioResistS2CPacket.STREAM_CODEC,
                ImperioResistS2CPacket::handleClient);
        registrar.playToClient(
                ImperioVictimBoundS2CPacket.TYPE,
                ImperioVictimBoundS2CPacket.STREAM_CODEC,
                ImperioVictimBoundS2CPacket::handleClient);
        registrar.playToClient(
                CrucioIntentFeedbackS2CPacket.TYPE,
                CrucioIntentFeedbackS2CPacket.STREAM_CODEC,
                CrucioIntentFeedbackS2CPacket::handleClient);

        registrar.playToServer(
                ImperioCommandC2SPacket.TYPE,
                ImperioCommandC2SPacket.STREAM_CODEC,
                ImperioCommandC2SPacket::handle);
        registrar.playToServer(
                ImperioResistC2SPacket.TYPE,
                ImperioResistC2SPacket.STREAM_CODEC,
                ImperioResistC2SPacket::handle);
    }
}
