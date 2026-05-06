package at.koopro.wizardsandbeasts.network;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

final class ModNetworkSkills {

    private ModNetworkSkills() {}

    static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SkillDataSyncS2CPacket.TYPE,
                SkillDataSyncS2CPacket.STREAM_CODEC,
                SkillDataSyncS2CPacket::handleClient);
        registrar.playToClient(
                SkillBonusSyncS2CPayload.TYPE,
                SkillBonusSyncS2CPayload.STREAM_CODEC,
                SkillBonusSyncS2CPayload::handleClient);

        registrar.playToServer(
                SkillUnlockC2SPacket.TYPE,
                SkillUnlockC2SPacket.STREAM_CODEC,
                SkillUnlockC2SPacket::handle);
    }
}
