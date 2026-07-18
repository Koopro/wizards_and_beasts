package at.koopro.wizardsandbeasts.network.skill;

import at.koopro.wizardsandbeasts.client.ability.state.ClientAbilityGrantState;
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
        registrar.playToClient(
                SyncSkillDefinitionsPayload.TYPE,
                SyncSkillDefinitionsPayload.STREAM_CODEC,
                ClientPayloadHandlers::handleSyncSkillDefinitions);
        // Derived ability-grant snapshot. Handled by its own client mirror (not ClientPayloadHandlers) so
        // this stays a standalone, tangle-free diff; the mirror is class-init-safe for dedicated servers.
        registrar.playToClient(
                AbilityGrantsSyncS2CPayload.TYPE,
                AbilityGrantsSyncS2CPayload.STREAM_CODEC,
                ClientAbilityGrantState::handle);

        registrar.playToServer(
                SkillUnlockC2SPayload.TYPE,
                SkillUnlockC2SPayload.STREAM_CODEC,
                SkillUnlockC2SPayload::handle);
    }
}
