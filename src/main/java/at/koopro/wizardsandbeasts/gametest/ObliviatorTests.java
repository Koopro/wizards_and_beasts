package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.ministry.trace.CastSurvey;
import at.koopro.wizardsandbeasts.ministry.trace.Exposure;
import at.koopro.wizardsandbeasts.ministry.trace.Incident;
import at.koopro.wizardsandbeasts.ministry.trace.LegalClass;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryCaseData;
import at.koopro.wizardsandbeasts.ministry.trace.MinistryTrace;
import at.koopro.wizardsandbeasts.ministry.trace.ObliviatorEntity;
import at.koopro.wizardsandbeasts.ministry.trace.Obliviators;
import at.koopro.wizardsandbeasts.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;

import java.util.List;
import java.util.Optional;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * C2 (systems audit 2026-11-25): a breach of the Statute of Secrecy brings one Obliviator, the Muggles who saw it
 * forget, and Hogsmeade's keepers were never Muggles to begin with (PoA ch.5: the only all-wizard village).
 */
public final class ObliviatorTests {

    private ObliviatorTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("obliviator_answers_a_breach_and_the_muggles_forget",
                "obliviators: one Apparates to a breach, Obliviates its Muggle witnesses, and Disapparates",
                ObliviatorTests::answersABreach);
        tests.add("hogsmeade_keepers_are_no_muggle_witnesses",
                "obliviators: wizarding shopkeepers do not count as Muggles; a plain villager does",
                ObliviatorTests::keepersAreNoMuggles);
    }

    private static void answersABreach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer caster = WizardTestSupport.placeMockPlayer(helper, "CarelessCaster", GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, caster);
        var muggle = helper.spawn(EntityType.VILLAGER, new BlockPos(4, 1, 0));
        muggle.setNoAi(true);
        muggle.setNoGravity(true);

        MinistryCaseData data = MinistryCaseData.get(level.getServer());
        Incident incident = new Incident(data.allocateId(), caster.getUUID(), "CarelessCaster", "lumos_maxima",
                level.dimension().identifier(), muggle.blockPosition(), MinistryTrace.now(level.getServer()),
                LegalClass.UNRESTRICTED, Exposure.MODERATE, false, false, 1, List.of(), Optional.empty(), false);
        data.putIncident(incident);
        data.recordWitnesses(incident.id(), List.of(muggle.getUUID()));

        ObliviatorEntity obliviator = Obliviators.dispatch(level.getServer(), data, incident);
        check(helper, obliviator != null, () -> "no Obliviator was sent to a loaded breach");
        int sent = level.getEntitiesOfClass(ObliviatorEntity.class, muggle.getBoundingBox().inflate(8),
                o -> o.incidentId() == incident.id()).size();
        check(helper, sent == 1, () -> sent + " Obliviators came to one breach");
        check(helper, data.witnessEntities(incident.id()).contains(muggle.getUUID()),
                () -> "the Muggle forgot before the Obliviator cast");

        helper.runAfterDelay(30, () -> {
            check(helper, data.witnessEntities(incident.id()).isEmpty(), () -> "the Muggle still remembers");
            check(helper, muggle.isAlive(), () -> "the Obliviator hurt the witness");
        });
        helper.runAfterDelay(95, () -> {
            try {
                check(helper, obliviator.isRemoved(), () -> "the Obliviator never Disapparated");
                helper.succeed();
            } finally {
                muggle.discard();
                WizardTestSupport.retire(helper, caster);
            }
        });
    }

    private static void keepersAreNoMuggles(GameTestHelper helper) {
        var keeper = helper.spawn(ModEntities.SETTLEMENT_NPC.get(), new BlockPos(1, 1, 1));
        keeper.setRoleId("wizards_and_beasts:honeydukes");
        keeper.setNoAi(true);
        var villager = helper.spawn(EntityType.VILLAGER, new BlockPos(3, 1, 1));
        villager.setNoAi(true);
        try {
            check(helper, !CastSurvey.isMuggle(keeper), () -> "a Hogsmeade shopkeeper counted as a Muggle witness");
            check(helper, CastSurvey.isMuggle(villager), () -> "a plain villager no longer counts as a Muggle");
            helper.succeed();
        } finally {
            keeper.discard();
            villager.discard();
        }
    }
}
