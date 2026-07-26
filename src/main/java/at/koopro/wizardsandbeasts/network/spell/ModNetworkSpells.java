package at.koopro.wizardsandbeasts.network.spell;

import at.koopro.wizardsandbeasts.network.spell.AvadaBlastS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDeniedS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.CrucioIntentFeedbackS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioCommandC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioControlS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioResistS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ImperioVictimBoundS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.PatronusFormSetS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ProtegoAnimationS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.ProtegoSpawnS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellAssignC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellCastC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataDeltaS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellDataSyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellImpactBurstS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellLeviosaAdjustC2SPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellProficiencySyncS2CPayload;
import at.koopro.wizardsandbeasts.network.spell.SpellSelectC2SPayload;

import at.koopro.wizardsandbeasts.client.spell.network.SpellClientPayloadHandlers;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworkSpells {

    private ModNetworkSpells() {}

    public static void register(PayloadRegistrar registrar) {
        registrar.playToServer(
                SpellCastC2SPayload.TYPE,
                SpellCastC2SPayload.STREAM_CODEC,
                SpellCastC2SPayload::handle);

        registrar.playToServer(
                SpellSelectC2SPayload.TYPE,
                SpellSelectC2SPayload.STREAM_CODEC,
                SpellSelectC2SPayload::handle);

        registrar.playToServer(
                SpellAssignC2SPayload.TYPE,
                SpellAssignC2SPayload.STREAM_CODEC,
                SpellAssignC2SPayload::handle);

        registrar.playToServer(
                SpellLeviosaAdjustC2SPayload.TYPE,
                SpellLeviosaAdjustC2SPayload.STREAM_CODEC,
                SpellLeviosaAdjustC2SPayload::handle);

        registrar.playToClient(
                SpellDataSyncS2CPayload.TYPE,
                SpellDataSyncS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleSpellDataSync);

        registrar.playToClient(
                SpellDataDeltaS2CPayload.TYPE,
                SpellDataDeltaS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleSpellDataDelta);
        registrar.playToClient(
                SpellProficiencySyncS2CPayload.TYPE,
                SpellProficiencySyncS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleSpellProficiencySync);

        registrar.playToClient(
                SpellDeniedS2CPayload.TYPE,
                SpellDeniedS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleSpellDenied);

        registrar.playToClient(
                AvadaBlastS2CPayload.TYPE,
                AvadaBlastS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleAvadaBlast);

        registrar.playToClient(
                SpellImpactBurstS2CPayload.TYPE,
                SpellImpactBurstS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleSpellImpactBurst);
        registrar.playToClient(
                BeamChannelS2CPayload.TYPE,
                BeamChannelS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleBeamChannel);
        registrar.playToClient(
                ProtegoSpawnS2CPayload.TYPE,
                ProtegoSpawnS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleProtegoSpawn);
        registrar.playToClient(
                ProtegoAnimationS2CPayload.TYPE,
                ProtegoAnimationS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleProtegoAnimation);

        registrar.playToClient(
                PatronusFormSetS2CPayload.TYPE,
                PatronusFormSetS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handlePatronusFormSet);
        registrar.playToClient(
                ImperioControlS2CPayload.TYPE,
                ImperioControlS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleImperioControl);
        registrar.playToClient(
                ImperioResistS2CPayload.TYPE,
                ImperioResistS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleImperioResist);
        registrar.playToClient(
                ImperioVictimBoundS2CPayload.TYPE,
                ImperioVictimBoundS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleImperioVictimBound);
        registrar.playToClient(
                CrucioIntentFeedbackS2CPayload.TYPE,
                CrucioIntentFeedbackS2CPayload.STREAM_CODEC,
                SpellClientPayloadHandlers::handleCrucioIntentFeedback);

        registrar.playToServer(
                ImperioCommandC2SPayload.TYPE,
                ImperioCommandC2SPayload.STREAM_CODEC,
                ImperioCommandC2SPayload::handle);
        registrar.playToServer(
                ImperioResistC2SPayload.TYPE,
                ImperioResistC2SPayload.STREAM_CODEC,
                ImperioResistC2SPayload::handle);
    }
}
