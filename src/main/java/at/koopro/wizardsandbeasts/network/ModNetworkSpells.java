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
                AvadaBlastS2CPacket.TYPE,
                AvadaBlastS2CPacket.STREAM_CODEC,
                AvadaBlastS2CPacket::handleClient);
    }
}
