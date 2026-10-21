package at.koopro.wizardsandbeasts.owl.career.track;

import at.koopro.wizardsandbeasts.bestiary.DiscoveryTier;
import at.koopro.wizardsandbeasts.owl.OWLGrade;
import at.koopro.wizardsandbeasts.owl.OWLSubject;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.ActiveContract;
import at.koopro.wizardsandbeasts.owl.career.track.PlayerCareerProgress.TrackRecord;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The career arithmetic on a small hand-built track: caps, contract progress, the board, promotion. */
class CareerRulesTest {

    private static final Identifier NIFFLER = Identifier.fromNamespaceAndPath("wizards_and_beasts", "niffler");
    private static final Identifier UNICORN = Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn");
    private static final Identifier MOONCALF = Identifier.fromNamespaceAndPath("wizards_and_beasts", "mooncalf");

    private static ActivityFilter tierAtLeast(DiscoveryTier tier, int rating) {
        return new ActivityFilter(ActivityType.BESTIARY_TIER, List.of(), Optional.of(tier), rating);
    }

    private static ContractDefinition contract(String id, String rank, ContractDefinition.Objective objective) {
        return new ContractDefinition(id, rank, objective, 3, 1, 10, new ContractDefinition.Reward(100, 50));
    }

    private static final CareerTrack TRACK = new CareerTrack(
            List.of(
                    new CareerTrack.Rank("trainee", 0, List.of(), 1.0f, List.of()),
                    new CareerTrack.Rank("field", 100, List.of(
                            new CareerGoal.BestiaryPages(DiscoveryTier.OBSERVED, 2, 0)), 1.25f, List.of()),
                    new CareerTrack.Rank("master", 500, List.of(
                            new CareerGoal.OwlGrade(OWLSubject.CARE_OF_MAGICAL_CREATURES, OWLGrade.O)), 2.0f, List.of())),
            List.of(
                    new CareerTrack.XpRule(tierAtLeast(DiscoveryTier.STUDIED, 0), 20, 40),
                    new CareerTrack.XpRule(ActivityFilter.of(ActivityType.BESTIARY_TIER), 5, 0),
                    new CareerTrack.XpRule(ActivityFilter.of(ActivityType.CREATURE_FED), 3, 6)),
            List.of(
                    contract("survey", "trainee",
                            new ContractDefinition.Activity(tierAtLeast(DiscoveryTier.OBSERVED, 0), 2, true)),
                    contract("feed", "trainee",
                            new ContractDefinition.Activity(ActivityFilter.of(ActivityType.CREATURE_FED), 3, false)),
                    contract("hair", "field", new ContractDefinition.Delivery(
                            Identifier.fromNamespaceAndPath("wizards_and_beasts", "unicorn_hair"), 2)),
                    contract("dangerous", "master",
                            new ContractDefinition.Activity(tierAtLeast(DiscoveryTier.STUDIED, 4), 1, false))),
            2, 3);

    private static CareerActivity page(Identifier species, DiscoveryTier tier, int rating) {
        return new CareerActivity(ActivityType.BESTIARY_TIER, species, tier, rating);
    }

    private static CareerActivity fed(Identifier species) {
        return CareerActivity.of(ActivityType.CREATURE_FED, species, 0);
    }

    /** A context backed by plain values. */
    private record Ctx(int pages, OWLGrade careGrade) implements CareerContext {
        @Override public OWLGrade grade(OWLSubject subject) { return careGrade; }
        @Override public int pages(DiscoveryTier minTier, int minRating) { return pages; }
        @Override public int researchCompleted() { return 0; }
        @Override public int activityTotal(ActivityFilter filter, boolean distinct) { return 0; }
        @Override public int contractsCompleted() { return 0; }
    }

    @Test
    void theTrackValidates() {
        assertEquals(List.of(), TRACK.validate());
    }

    @Test
    void aTrackWithBadRanksIsRefused() {
        CareerTrack broken = new CareerTrack(
                List.of(new CareerTrack.Rank("a", 10, List.of(), 1f, List.of()),
                        new CareerTrack.Rank("b", 5, List.of(), 1f, List.of())),
                List.of(), List.of(contract("x", "nowhere", new ContractDefinition.Activity(
                        ActivityFilter.of(ActivityType.CREATURE_FED), 1, false))), 2, 3);
        List<String> problems = broken.validate();
        assertEquals(3, problems.size(), problems.toString());
    }

    @Test
    void experienceUsesTheFirstMatchingRuleWithinItsDailyCap() {
        TrackRecord record = TrackRecord.FRESH;
        CareerRules.XpAward first = CareerRules.awardXp(TRACK, record, page(NIFFLER, DiscoveryTier.STUDIED, 3), 7);
        assertEquals(20, first.xp());
        CareerRules.XpAward second = CareerRules.awardXp(TRACK, first.record(), page(UNICORN, DiscoveryTier.KNOWN, 4), 7);
        assertEquals(20, second.xp());
        CareerRules.XpAward capped = CareerRules.awardXp(TRACK, second.record(), page(MOONCALF, DiscoveryTier.STUDIED, 2), 7);
        assertEquals(0, capped.xp(), "the studied rule is capped at 40 a day");
        CareerRules.XpAward lower = CareerRules.awardXp(TRACK, capped.record(), page(MOONCALF, DiscoveryTier.OBSERVED, 2), 7);
        assertEquals(5, lower.xp(), "an observed page matches the uncapped rule");
        CareerRules.XpAward nextDay = CareerRules.awardXp(TRACK, lower.record(), page(MOONCALF, DiscoveryTier.STUDIED, 2), 8);
        assertEquals(20, nextDay.xp(), "the cap resets the next day");
        assertEquals(65, nextDay.record().xp());
    }

    @Test
    void aCapThatDoesNotDivideEvenlyPaysTheRemainder() {
        TrackRecord record = TrackRecord.FRESH;
        int total = 0;
        for (int i = 0; i < 5; i++) {
            CareerRules.XpAward award = CareerRules.awardXp(TRACK, record, fed(NIFFLER), 3);
            total += award.xp();
            record = award.record();
        }
        assertEquals(6, total, "3 xp per feed, capped at 6 a day");
    }

    @Test
    void distinctContractsCountEachCreatureOnce() {
        TrackRecord record = TrackRecord.FRESH.withActive(List.of(ActiveContract.taken("survey", 1)));
        CareerRules.ContractAdvance a = CareerRules.advanceContracts(TRACK, record, page(NIFFLER, DiscoveryTier.OBSERVED, 3));
        CareerRules.ContractAdvance b = CareerRules.advanceContracts(TRACK, a.record(), page(NIFFLER, DiscoveryTier.STUDIED, 3));
        assertEquals(1, b.record().activeContract("survey").progress(), "the same creature twice counts once");
        CareerRules.ContractAdvance c = CareerRules.advanceContracts(TRACK, b.record(), page(UNICORN, DiscoveryTier.OBSERVED, 4));
        assertEquals(1, c.finished().size());
        assertNull(c.record().activeContract("survey"), "a finished contract leaves the hand");
    }

    @Test
    void contractsIgnoreActivityTheyDoNotAskFor() {
        TrackRecord record = TrackRecord.FRESH.withActive(List.of(ActiveContract.taken("survey", 1),
                ActiveContract.taken("feed", 1)));
        CareerRules.ContractAdvance advance = CareerRules.advanceContracts(TRACK, record,
                page(NIFFLER, DiscoveryTier.ENCOUNTERED, 3));
        assertEquals(0, advance.record().activeContract("survey").progress(), "an encountered page is not observed");
        assertEquals(0, advance.record().activeContract("feed").progress());
        CareerRules.ContractAdvance fedOnce = CareerRules.advanceContracts(TRACK, record, fed(MOONCALF));
        assertEquals(1, fedOnce.record().activeContract("feed").progress());
    }

    @Test
    void contractsExpireAfterTheirDuration() {
        ContractDefinition def = TRACK.contract("survey");
        assertNotNull(def);
        ActiveContract taken = ActiveContract.taken("survey", 10);
        assertFalse(CareerRules.expired(def, taken, 12), "day 3 of a 3-day contract is still in time");
        assertTrue(CareerRules.expired(def, taken, 13));
    }

    @Test
    void theBoardIsStableAndRespectsRank() {
        List<ContractDefinition> a = CareerRules.offers(TRACK, TrackRecord.FRESH, 42L, 5);
        List<ContractDefinition> b = CareerRules.offers(TRACK, TrackRecord.FRESH, 42L, 5);
        assertEquals(a, b, "the same day's board is the same every time it is opened");
        assertTrue(a.stream().allMatch(c -> c.minRank().equals("trainee")), "a trainee saw a senior contract");
        List<ContractDefinition> master = CareerRules.offers(TRACK, TrackRecord.FRESH.withRank(2), 42L, 5);
        assertEquals(3, master.size(), "board size caps the offers");
    }

    @Test
    void theBoardStrikesOffHeldRecentAndAbandonedContracts() {
        TrackRecord held = TrackRecord.FRESH.withActive(List.of(ActiveContract.taken("survey", 5)));
        assertTrue(CareerRules.offers(TRACK, held, 1L, 5).stream().noneMatch(c -> c.id().equals("survey")));
        TrackRecord done = TrackRecord.FRESH.completed("feed", 5);
        assertTrue(CareerRules.offers(TRACK, done, 1L, 5).stream().noneMatch(c -> c.id().equals("feed")),
                "a contract finished today is not offered again today");
        TrackRecord dropped = TrackRecord.FRESH.abandoned("survey", 5);
        assertTrue(CareerRules.offers(TRACK, dropped, 1L, 5).stream().noneMatch(c -> c.id().equals("survey")));
        assertTrue(CareerRules.offers(TRACK, dropped, 1L, 6).stream().anyMatch(c -> c.id().equals("survey")),
                "an abandoned contract returns the next day");
    }

    @Test
    void promotionNeedsBothExperienceAndMilestones() {
        TrackRecord rich = TrackRecord.FRESH.withXp(1000);
        assertFalse(CareerRules.canPromote(TRACK, rich, new Ctx(1, OWLGrade.T)), "xp alone promoted");
        assertTrue(CareerRules.canPromote(TRACK, rich, new Ctx(2, OWLGrade.T)));
        assertFalse(CareerRules.canPromote(TRACK, TrackRecord.FRESH.withXp(99), new Ctx(9, OWLGrade.O)),
                "milestones alone promoted");
        TrackRecord field = rich.withRank(1);
        assertFalse(CareerRules.canPromote(TRACK, field, new Ctx(9, OWLGrade.E)));
        assertTrue(CareerRules.canPromote(TRACK, field, new Ctx(9, OWLGrade.O)));
        assertFalse(CareerRules.canPromote(TRACK, field.withRank(2), new Ctx(9, OWLGrade.O)), "past the top rank");
    }

    @Test
    void lifetimeTotalsFilterBySpeciesRatingAndDistinct() {
        TrackRecord record = TrackRecord.FRESH;
        record = CareerRules.count(record, fed(NIFFLER));
        record = CareerRules.count(record, fed(NIFFLER));
        record = CareerRules.count(record, fed(UNICORN));
        Map<String, Integer> counters = record.counters();
        ActivityFilter anyFeed = ActivityFilter.of(ActivityType.CREATURE_FED);
        assertEquals(3, CareerRules.activityTotal(counters, anyFeed, false, s -> 0));
        assertEquals(2, CareerRules.activityTotal(counters, anyFeed, true, s -> 0));
        ActivityFilter dangerous = new ActivityFilter(ActivityType.CREATURE_FED, List.of(), Optional.empty(), 4);
        assertEquals(1, CareerRules.activityTotal(counters, dangerous, false, s -> s.equals(UNICORN) ? 4 : 3));
    }

    @Test
    void theRecordRoundTrips() {
        TrackRecord record = CareerRules.count(TrackRecord.FRESH.withXp(77).withRank(1), fed(NIFFLER))
                .withActive(List.of(new ActiveContract("survey", 3, 1, List.of(NIFFLER.toString()))))
                .completed("feed", 2).abandoned("hair", 4);
        PlayerCareerProgress progress = PlayerCareerProgress.EMPTY.with("magizoologist", record);
        PlayerCareerProgress back = PlayerCareerProgress.CODEC.parse(JsonOps.INSTANCE,
                PlayerCareerProgress.CODEC.encodeStart(JsonOps.INSTANCE, progress).getOrThrow()).getOrThrow();
        assertEquals(progress, back);
    }
}
