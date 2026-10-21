package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.bestiary.BestiaryDataHelper;
import at.koopro.wizardsandbeasts.bestiary.CreatureProfile.StudyAct;
import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.bestiary.data.EntryHistory;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestRule;
import at.koopro.wizardsandbeasts.bestiary.harvest.HarvestTable;
import at.koopro.wizardsandbeasts.event.bestiary.BestiaryDiscoveryHandler;
import at.koopro.wizardsandbeasts.network.bestiary.BestiaryDataSyncPayload;
import at.koopro.wizardsandbeasts.owl.OWLGradeCalculator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/** The field journal's server half: history, study acts, research completion and its rewards, and the sync. */
public final class JournalTests {

    private static final Identifier MOONCALF = id("mooncalf");
    private static final Identifier NIFFLER = id("niffler");

    private JournalTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("journal_history_records_the_first_meeting", "journal: depth by depth, with where it was first met",
                JournalTests::historyRecordsFirstMeeting);
        tests.add("journal_study_acts_reach_the_client", "journal: a study act is recorded and synced to the player",
                JournalTests::studyActsReachClient);
        tests.add("journal_research_completes_once_and_pays", "journal: completed research is granted once and rewards",
                JournalTests::researchCompletesOnceAndPays);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(WizardsAndBeastsMod.MODID, path);
    }

    private static void historyRecordsFirstMeeting(GameTestHelper helper) {
        ServerPlayer naturalist = WizardTestSupport.placeMockPlayer(helper, "JournalHistory", GameType.SURVIVAL);
        try {
            long day = naturalist.level().getGameTime() / 24000L;
            BestiaryDataHelper.setTier(naturalist, NIFFLER, DiscoveryTier.ENCOUNTERED);
            BestiaryDataHelper.setTier(naturalist, NIFFLER, DiscoveryTier.OBSERVED);
            EntryHistory history = BestiaryDataHelper.getData(naturalist).historyOf(NIFFLER);
            check(helper, history.firstSeenDay() == day && !history.firstSeenBiome().isEmpty()
                            && history.firstSeenWorld().equals("minecraft:overworld"),
                    () -> "the first meeting was not recorded: " + history);
            check(helper, history.dayReached(DiscoveryTier.OBSERVED) == day && history.dayReached(DiscoveryTier.KNOWN) < 0,
                    () -> "depth days wrong: " + history.reachedDay());
            BestiaryDataHelper.setTier(naturalist, NIFFLER, DiscoveryTier.OBSERVED); // same depth again
            check(helper, BestiaryDataHelper.getData(naturalist).historyOf(NIFFLER).equals(history),
                    () -> "re-reaching a depth rewrote the history");
            helper.succeed();
        } finally {
            retire(helper, naturalist);
        }
    }

    private static void studyActsReachClient(GameTestHelper helper) {
        ServerPlayer naturalist = WizardTestSupport.placeMockPlayer(helper, "JournalSync", GameType.SURVIVAL);
        try {
            EntityType<?> niffler = BuiltInRegistries.ENTITY_TYPE.getValue(NIFFLER);
            WizardTestSupport.drainClientboundPayloads(naturalist);
            BestiaryDiscoveryHandler.studied(naturalist, niffler, StudyAct.FEED);
            check(helper, BestiaryDataHelper.getData(naturalist).historyOf(NIFFLER).acts().contains("feed"),
                    () -> "feeding was not recorded as a study act");
            BestiaryDataSyncPayload last = null;
            for (CustomPacketPayload payload : WizardTestSupport.drainClientboundPayloads(naturalist)) {
                if (payload instanceof BestiaryDataSyncPayload sync) {
                    last = sync;
                }
            }
            BestiaryDataSyncPayload sent = last;
            check(helper, sent != null && sent.data().historyOf(NIFFLER).acts().contains("feed")
                            && sent.data().tiers().getOrDefault(NIFFLER, DiscoveryTier.UNKNOWN).atLeast(DiscoveryTier.ENCOUNTERED),
                    () -> "the client was not told: " + (sent == null ? "no bestiary sync sent" : sent.data().history()));
            helper.succeed();
        } finally {
            retire(helper, naturalist);
        }
    }

    /**
     * A Mooncalf studies by feeding and bonding and has five driven behaviours, so three noted ones complete it.
     * Each step is taken through the real server door; completion must land on the last one, once, and be read by
     * the species trust, the O.W.L. evidence and the harvest cooldown.
     */
    private static void researchCompletesOnceAndPays(GameTestHelper helper) {
        ServerPlayer naturalist = WizardTestSupport.placeMockPlayer(helper, "JournalResearch", GameType.SURVIVAL);
        try {
            EntityType<?> mooncalf = BuiltInRegistries.ENTITY_TYPE.getValue(MOONCALF);
            BestiaryDataHelper.setTier(naturalist, MOONCALF, DiscoveryTier.KNOWN);
            BestiaryDataHelper.recordStudyAct(naturalist, MOONCALF, StudyAct.FEED);
            BestiaryDataHelper.recordStudyAct(naturalist, MOONCALF, StudyAct.BOND);
            BestiaryDataHelper.addFieldNote(naturalist, MOONCALF, "prey");
            BestiaryDataHelper.addFieldNote(naturalist, MOONCALF, "herd");
            check(helper, !BestiaryDataHelper.researchComplete(naturalist, mooncalf),
                    () -> "research completed with two behaviours of five noted");
            check(helper, !at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld.letsNear(naturalist, mooncalf, false, ""),
                    () -> "setup: a Mooncalf let a standing, unresearched player near");
            int before = OWLGradeCalculator.evidence(naturalist).researchComplete();
            BestiaryDataHelper.addFieldNote(naturalist, MOONCALF, "nocturnal");
            check(helper, BestiaryDataHelper.researchComplete(naturalist, mooncalf),
                    () -> "research did not complete: " + BestiaryDataHelper.getData(naturalist).historyOf(MOONCALF));
            check(helper, at.koopro.wizardsandbeasts.creature.wildlife.WildlifeWorld.letsNear(naturalist, mooncalf, false, ""),
                    () -> "a researched naturalist must not need to creep up on a Mooncalf");
            check(helper, OWLGradeCalculator.evidence(naturalist).researchComplete() == before + 1,
                    () -> "the O.W.L. does not count the completed study");
            BestiaryDataHelper.addFieldNote(naturalist, MOONCALF, "cowardly"); // further progress changes nothing
            check(helper, BestiaryDataHelper.completedResearch(naturalist) == before + 1,
                    () -> "research was granted twice");

            HarvestRule rule = HarvestTable.all().values().stream().filter(r -> r.cooldownSeconds() > 0).findFirst().orElse(null);
            if (rule != null) {
                long cooldown = rule.cooldownSeconds() * 20L;
                long last = 1_000_000L;
                long halfway = last + cooldown / 2;
                check(helper, HarvestGate.isOffCooldown(rule, last, halfway, true)
                                && !HarvestGate.isOffCooldown(rule, last, halfway, false),
                        () -> "completed research does not halve the harvest wait");
            }
            helper.succeed();
        } finally {
            retire(helper, naturalist);
        }
    }
}
