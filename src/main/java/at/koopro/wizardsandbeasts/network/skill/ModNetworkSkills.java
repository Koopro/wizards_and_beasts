package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.client.network.ClientPayloadHandlers;
import at.koopro.wizardsandbeasts.network.skill.SkillBonusSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.skill.SkillUnlockC2SPayload;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkSkills {

    private ModNetworkSkills() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                SkillDataSyncS2CPayload.TYPE,
                SkillDataSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSkillDataSync);
        registrar.playToClient(
                SkillBonusSyncS2CPayload.TYPE,
                SkillBonusSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSkillBonusSync);
        registrar.playToClient(
                VocationDataSyncS2CPayload.TYPE,
                VocationDataSyncS2CPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleVocationDataSync);

        registrar.playToServer(
                SkillUnlockC2SPayload.TYPE,
                SkillUnlockC2SPayload.STREAM_CODEC,
                SkillUnlockC2SPayload::handle);
    }
}
