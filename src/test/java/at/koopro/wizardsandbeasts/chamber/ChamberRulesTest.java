package at.koopro.wizardsandbeasts.chamber;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static at.koopro.wizardsandbeasts.chamber.ChamberEncounter.Action;
import static at.koopro.wizardsandbeasts.chamber.ChamberEncounter.Phase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Chamber's rules without a world: the encounter, the quest's stages, and when the sword may be given. */
class ChamberRulesTest {

    @Test
    void aSleepingChamberWakesOnlyForSomeoneInItsHallWithTheModuleOn() {
        assertSame(Action.WAKE, ChamberEncounter.decide(Phase.SLEEPING, true, true, false, 0));
        assertSame(Action.NOTHING, ChamberEncounter.decide(Phase.SLEEPING, true, false, false, 0));
        assertSame(Action.NOTHING, ChamberEncounter.decide(Phase.SLEEPING, false, true, false, 0),
                "a switched-off Chamber woke");
    }

    @Test
    void anAwakeChamberWaitsForItsHallToStandEmptyBeforePuttingTheBasiliskBack() {
        assertSame(Action.NOTHING, ChamberEncounter.decide(Phase.AWAKE, true, true, true, 0));
        assertSame(Action.NOTHING,
                ChamberEncounter.decide(Phase.AWAKE, true, false, true, ChamberEncounter.RESET_TICKS - 1));
        assertSame(Action.PUT_BACK,
                ChamberEncounter.decide(Phase.AWAKE, true, false, true, ChamberEncounter.RESET_TICKS));
        assertSame(Action.FORGET, ChamberEncounter.decide(Phase.AWAKE, true, true, false, 0),
                "a basilisk gone without dying must not leave the Chamber awake for ever");
    }

    @Test
    void aClearedChamberStaysCleared() {
        for (boolean module : new boolean[] {true, false}) {
            for (boolean someone : new boolean[] {true, false}) {
                assertSame(Action.NOTHING, ChamberEncounter.decide(Phase.SLAIN, module, someone, false, 99_999));
            }
        }
    }

    @Test
    void theQuestPointsAtTheNextStageInOrderAndStopsOnceTheBasiliskIsDead() {
        assertNull(ChamberStage.next(Set.of()), "no chart, no quest");
        assertSame(ChamberStage.SEAL_OPENED, ChamberStage.next(EnumSet.of(ChamberStage.CHARTED)));
        assertSame(ChamberStage.GATE_OPENED,
                ChamberStage.next(EnumSet.of(ChamberStage.CHARTED, ChamberStage.SEAL_OPENED)));
        assertSame(ChamberStage.BASILISK_SLAIN, ChamberStage.next(EnumSet.of(ChamberStage.CHARTED,
                ChamberStage.SEAL_OPENED, ChamberStage.GATE_OPENED, ChamberStage.HALL_ENTERED)));
        assertNull(ChamberStage.next(EnumSet.of(ChamberStage.CHARTED, ChamberStage.BASILISK_SLAIN)));
        // Found the Chamber without a chart (a cache map, or by chance): the quest still guides.
        assertSame(ChamberStage.GATE_OPENED, ChamberStage.next(EnumSet.of(ChamberStage.SEAL_OPENED)));
    }

    @Test
    void onlyASlayerIsWorthyOfTheHatsRecall() {
        assertFalse(ChamberQuest.EMPTY.with(ChamberStage.HALL_ENTERED).worthy());
        assertTrue(ChamberQuest.EMPTY.with(ChamberStage.BASILISK_SLAIN).worthy());
    }

    @Test
    void theQuestRoundTripsAndKeepsStagesThisBuildDoesNotKnow() {
        ChamberQuest quest = new ChamberQuest(Set.of("charted", "seal_opened", "from_a_newer_build"));
        var json = ChamberQuest.CODEC.encodeStart(JsonOps.INSTANCE, quest).getOrThrow();
        ChamberQuest back = ChamberQuest.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(quest, back);
        assertEquals(EnumSet.of(ChamberStage.CHARTED, ChamberStage.SEAL_OPENED), back.stages());
    }

    @Test
    void aChamberStandsOnlyOnDryListedGroundThatCoversItsHall() {
        assertTrue(at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.suits(true, false, 70, 66));
        int drop = at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.MAX_DROP;
        assertTrue(at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.suits(true, false, 70, 70 - drop));
        assertFalse(at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.suits(true, false, 70, 70 - drop - 1),
                "the hall would stand out of the hillside");
        assertFalse(at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.suits(false, false, 70, 70),
                "the ring would stand in a biome the Chamber does not list");
        assertFalse(at.koopro.wizardsandbeasts.chamber.structure.ChamberSite.suits(true, true, 70, 70),
                "the ring would stand in water");
        assertTrue(drop < 20, "the hall's roof is 20 below the ring: the rule must leave ground over it");
    }

    @Test
    void theSwordRisesOnlyWhileNoneIsOut() {
        assertTrue(ChamberSword.mayAward(true, false));
        assertFalse(ChamberSword.mayAward(true, true), "one sword per world");
        assertFalse(ChamberSword.mayAward(false, false), "artefacts switched off");
    }

    @Test
    void everyStageAndSignHasItsWords() throws IOException {
        Path lang = Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");
        Set<String> keys;
        try (Reader reader = Files.newBufferedReader(lang)) {
            keys = JsonParser.parseReader(reader).getAsJsonObject().keySet();
        }
        Set<String> missing = new TreeSet<>();
        for (ChamberStage stage : ChamberStage.values()) {
            require(keys, missing, stage.reachedKey());
        }
        for (ChamberStage stage : List.of(ChamberStage.SEAL_OPENED, ChamberStage.GATE_OPENED, ChamberStage.HALL_ENTERED,
                ChamberStage.BASILISK_SLAIN)) {
            require(keys, missing, stage.hintKey());
        }
        for (String sign : List.of("landing", "seal_ask", "seal_knock", "basin", "hall")) {
            for (int line = 1; line <= 4; line++) {
                require(keys, missing, "chamber.wizards_and_beasts.sign." + sign + "." + line);
            }
        }
        for (String key : List.of("chamber.wizards_and_beasts.title", "chamber.wizards_and_beasts.wakes",
                "chamber.wizards_and_beasts.wakes.body", "chamber.wizards_and_beasts.wakes.counsel",
                "chamber.wizards_and_beasts.sword_out", "chamber.wizards_and_beasts.chart.given",
                "item.wizards_and_beasts.serpent_chart", "artefact.wizards_and_beasts.sword_of_gryffindor.earned")) {
            require(keys, missing, key);
        }
        assertTrue(missing.isEmpty(), "missing lang keys: " + missing);
    }

    private static void require(Set<String> keys, Set<String> missing, String key) {
        if (!keys.contains(key)) {
            missing.add(key);
        }
    }
}
