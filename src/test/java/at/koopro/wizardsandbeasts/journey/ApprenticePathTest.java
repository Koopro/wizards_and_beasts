package at.koopro.wizardsandbeasts.journey;

import at.koopro.wizardsandbeasts.education.EducationService;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Apprentice's Path as rules: which steps apply, which are new, which comes next, and how the record saves. */
class ApprenticePathTest {

    /** A fresh wizard: wand hand, bestiary on, through the ceremony, nothing else. */
    private static ApprenticePath.Facts arrived() {
        return new ApprenticePath.Facts(true, true, true, false, false, false, false, false);
    }

    @Test
    void aWandHandWalksEveryStepInOrder() {
        assertEquals(List.of(PathStep.values()), ApprenticePath.applicable(arrived()));
    }

    @Test
    void aWandlessHeritageSkipsTheWandSteps() {
        var facts = new ApprenticePath.Facts(false, true, true, false, false, false, false, false);
        assertEquals(List.of(PathStep.ARRIVE, PathStep.FIELD_STUDY), ApprenticePath.applicable(facts));
    }

    @Test
    void theBestiarySwitchedOffDropsFieldStudy() {
        var facts = new ApprenticePath.Facts(true, false, true, false, false, false, false, false);
        assertFalse(ApprenticePath.applicable(facts).contains(PathStep.FIELD_STUDY));
    }

    @Test
    void nothingIsPaidBeforeTheCeremony() {
        // Somehow already holding a wand and having watched a creature: still nothing before a heritage is chosen.
        var facts = new ApprenticePath.Facts(true, true, false, true, false, false, false, true);
        assertTrue(ApprenticePath.newlyMet(PathRecord.EMPTY, facts).isEmpty());
    }

    @Test
    void stepsMayBeMetOutOfOrderAndCurrentIsTheFirstUnfinished() {
        // Watched a bowtruckle before owning a wand.
        var facts = new ApprenticePath.Facts(true, true, true, false, false, false, false, true);
        List<PathStep> fresh = ApprenticePath.newlyMet(PathRecord.EMPTY, facts);
        assertEquals(List.of(PathStep.ARRIVE, PathStep.FIELD_STUDY), fresh);

        PathRecord record = PathRecord.EMPTY.with(PathStep.ARRIVE).with(PathStep.FIELD_STUDY);
        assertSame(PathStep.WAND, ApprenticePath.current(record, facts));
    }

    @Test
    void aDoneStepIsNeverNewAgain() {
        var facts = new ApprenticePath.Facts(true, true, true, true, true, false, false, false);
        PathRecord record = PathRecord.EMPTY;
        for (PathStep step : ApprenticePath.newlyMet(record, facts)) {
            record = record.with(step);
        }
        assertTrue(ApprenticePath.newlyMet(record, facts).isEmpty(), "a second look must pay nothing");
        assertSame(PathStep.OWN_SPELL, ApprenticePath.current(record, facts));
    }

    @Test
    void aStepStaysDoneWhenItsFactStopsHolding() {
        // The wand was paid for, then lost.
        PathRecord record = PathRecord.EMPTY.with(PathStep.ARRIVE).with(PathStep.WAND);
        var facts = arrived();
        assertFalse(ApprenticePath.newlyMet(record, facts).contains(PathStep.WAND));
        assertSame(PathStep.FIRST_LIGHT, ApprenticePath.current(record, facts));
    }

    @Test
    void thePathIsWalkedWhenEveryApplicableStepIsDone() {
        var wandless = new ApprenticePath.Facts(false, true, true, false, false, false, false, true);
        PathRecord record = PathRecord.EMPTY.with(PathStep.ARRIVE).with(PathStep.FIELD_STUDY);
        assertTrue(ApprenticePath.walked(record, wandless));
        assertNull(ApprenticePath.current(record, wandless));
        assertFalse(ApprenticePath.walked(record, arrived()), "a wand hand still has the wand steps ahead");
    }

    @Test
    void theRecordRoundTripsAndKeepsStepsThisBuildDoesNotKnow() {
        PathRecord record = new PathRecord(Set.of("arrive", "wand", "from_a_newer_build"), Set.of("wand"), false);
        JsonElement json = PathRecord.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        PathRecord back = PathRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(record, back);
        assertTrue(back.done().contains("from_a_newer_build"));
    }

    @Test
    void aRecordSavedBeforeOwedAndGuidedExistedLoadsGuidedAndOwingNothing() {
        JsonObject json = new JsonObject();
        com.google.gson.JsonArray done = new com.google.gson.JsonArray();
        done.add("arrive");
        json.add("done", done);
        PathRecord record = PathRecord.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(record.guided());
        assertTrue(record.owed().isEmpty());
        assertTrue(record.has(PathStep.ARRIVE));
    }

    @Test
    void anEarnedStepIsOwedOnceAndPayingStrikesOnlyTheDebt() {
        PathRecord earned = PathRecord.EMPTY.earned(PathStep.WAND);
        assertTrue(earned.has(PathStep.WAND) && earned.owes(PathStep.WAND));
        PathRecord paid = earned.paid(PathStep.WAND);
        assertTrue(paid.has(PathStep.WAND), "paying must not undo the step");
        assertFalse(paid.owes(PathStep.WAND));
        assertSame(paid, paid.paid(PathStep.WAND), "a second payment of the same debt changes nothing");
        assertFalse(paid.earned(PathStep.WAND).owes(PathStep.WAND), "a done step can never be owed again");
    }

    @Test
    void aReconciledStepIsDoneButNeverOwed() {
        PathRecord reconciled = PathRecord.EMPTY.with(PathStep.ARRIVE).with(PathStep.WAND);
        assertTrue(reconciled.owed().isEmpty());
        assertTrue(ApprenticePath.newlyMet(reconciled,
                new ApprenticePath.Facts(true, true, true, true, false, false, false, false)).isEmpty());
    }

    @Test
    void aSwitchedOffSystemIsNeitherPaidNorPromised() {
        var all = new PathReward.Systems(true, true);
        var none = new PathReward.Systems(false, false);
        assertEquals(List.of(PathReward.HANDBOOK, PathReward.RECIPES), PathReward.of(PathStep.ARRIVE, all));
        assertEquals(List.of(PathReward.RECIPES), PathReward.of(PathStep.ARRIVE, none));
        assertEquals(List.of(PathReward.SKILL_POINT, PathReward.DUELLING_DUMMY), PathReward.of(PathStep.OWN_SPELL, all));
        assertEquals(List.of(PathReward.DUELLING_DUMMY), PathReward.of(PathStep.OWN_SPELL, none));
        for (PathStep step : PathStep.values()) {
            assertFalse(PathReward.of(step, none).isEmpty(), step + " pays nothing with its optional systems off");
        }
    }

    @Test
    void stepIdsAreStableAndResolve() {
        for (PathStep step : PathStep.values()) {
            assertSame(step, PathStep.byId(step.id()));
        }
        assertEquals("first_light", PathStep.FIRST_LIGHT.id());
        assertNull(PathStep.byId("nonsense"));
    }

    @Test
    void theEducationListLeadsWithTheStepAndStaysShort() {
        List<Component> four = List.of(Component.literal("a"), Component.literal("b"), Component.literal("c"),
                Component.literal("d"));
        List<Component> led = EducationService.withPathLead(four, PathStep.WAND);
        assertEquals(EducationService.MAX_NEXT, led.size());
        assertEquals(four, EducationService.withPathLead(four, null));
    }

    @Test
    void everyStepHasItsWords() throws IOException {
        Path lang = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        Set<String> keys;
        try (Reader reader = Files.newBufferedReader(lang)) {
            keys = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
        Set<String> missing = new TreeSet<>();
        for (PathStep step : PathStep.values()) {
            for (String key : List.of(step.titleKey(), step.hintKey())) {
                if (!keys.contains(key)) {
                    missing.add(key);
                }
            }
        }
        for (PathReward part : PathReward.values()) {
            if (!keys.contains(part.translationKey())) {
                missing.add(part.translationKey());
            }
        }
        String identity = "gui.wizards_and_beasts.character_sheet.identity.path.";
        for (String state : List.of("done", "now", "later", "callout")) {
            if (!keys.contains(identity + state)) {
                missing.add(identity + state);
            }
        }
        assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
    }
}
