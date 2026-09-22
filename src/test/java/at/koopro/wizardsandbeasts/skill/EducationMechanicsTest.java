package at.koopro.wizardsandbeasts.skill;

import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import at.koopro.wizardsandbeasts.wand.allegiance.WandAllegianceRules;
import at.koopro.wizardsandbeasts.wand.allegiance.WandBondHistory;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of the education rework that are arithmetic rather than world state.
 *
 * <p>Three things worth pinning: the new effect vocabulary is describable in English (a stat with no words is a
 * tooltip that reads "+25% null"), counts are printed as counts, and the version bump that deleted 34 node ids
 * really does hand every allocated point back — an allocation pointing at a node that no longer exists is
 * silently worth nothing, which is the one migration failure a player would never report as a bug.
 */
class EducationMechanicsTest {

    private static final Path LANG_FILE =
            Path.of("src", "main", "resources", "assets", "wizards_and_beasts", "lang", "en_us.json");

    private static JsonObject lang;

    @BeforeAll
    static void loadLang() throws IOException {
        lang = JsonParser.parseString(Files.readString(LANG_FILE)).getAsJsonObject();
    }

    @Test
    void everyGameplayStatCanBeSaidInEnglish() {
        List<String> missing = new ArrayList<>();
        for (GameplayStat stat : GameplayStat.values()) {
            String key = "skill.wizards_and_beasts.stat." + stat.name().toLowerCase(Locale.ROOT);
            if (!lang.has(key)) {
                missing.add(key);
            }
        }
        assertTrue(missing.isEmpty(), "stats with no wording: " + missing);
    }

    @Test
    void countsAreCountsAndFractionsAreFractions() {
        // A light ahead of you, a duel survived and a tier of trust are whole things; everything else is a
        // fraction. Printing "+100%" where the game means "+1" is a tooltip lying about its own effect.
        assertTrue(GameplayStat.LIGHT_REACH.isCount());
        assertTrue(GameplayStat.ALLEGIANCE_GRIP.isCount());
        assertTrue(GameplayStat.CREATURE_TRUST.isCount());
        assertFalse(GameplayStat.WARD_INTEGRITY.isCount());
        assertFalse(GameplayStat.CURSE_BACKLASH.isCount());
        assertFalse(GameplayStat.TRACE_DISCRETION.isCount());
        assertFalse(GameplayStat.HARVEST_BONUS_CHANCE.isCount());
    }

    @Test
    void theNewEffectLinesResolve() {
        for (String key : List.of("skill.wizards_and_beasts.effect.gameplay_count",
                "skill.wizards_and_beasts.effect.dark_study",
                "screen.wizards_and_beasts.skill_tree.practice",
                "screen.wizards_and_beasts.skill_tree.leads_to",
                "screen.wizards_and_beasts.skill_tree.mod_advancement",
                "screen.wizards_and_beasts.skill_tree.canon")) {
            assertTrue(lang.has(key), "missing " + key);
        }
    }

    @Test
    void darkStudyReachesARealSystem() {
        // The flag gates "safe to ship in a datapack". Dark study is consumed by DarkCorruptionService, so a
        // node may declare it; if that wiring is ever removed this is what fails.
        assertTrue(SkillEffectSummary.isImplemented(SkillEffect.Type.DARK_STUDY));
    }

    @Test
    void deletingNodeIdsRefundsEveryAllocatedPoint() {
        PlayerSkillData data = new PlayerSkillData();
        data.addSkillPoints(12);
        assertTrue(data.spendSkillPoints(7), "fixture could not spend the points it just granted");
        data.setSkillLevel("a_node_that_no_longer_exists", 1);
        data.setSkillLevel("another_deleted_node", 2);

        // A save written before the education rework.
        var tag = data.save();
        tag.putInt(PlayerSkillData.VERSION_KEY, 4);
        PlayerSkillData loaded = new PlayerSkillData();
        loaded.load(tag);

        assertTrue(loaded.needsWebMigration(), "a version 4 record must still be owed its refund");
        int cleared = loaded.applyWebMigration();
        assertEquals(2, cleared, "both stale allocations should have been cleared");
        assertEquals(loaded.getTotalPointsEarned(), loaded.getSkillPoints(),
                "every point earned must be unspent again after a node-deleting migration");
        assertFalse(loaded.needsWebMigration(), "the migration must not fire twice");
    }

    @Test
    void aGentleHandFindsMoreWithoutEverBeingCertain() {
        // Magizoology's harvesting line, as the tooltip states it: a quarter more often.
        assertEquals(0.5f, at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate
                .effectiveChance(0.4f, 0.25f), 0.0001f);
        assertEquals(0.4f, at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate
                .effectiveChance(0.4f, 0.0f), 0.0001f);
        // A guaranteed rule cannot become more than guaranteed, and a negative bonus cannot take anything away.
        assertEquals(1.0f, at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate
                .effectiveChance(1.0f, 0.9f), 0.0001f);
        assertEquals(0.4f, at.koopro.wizardsandbeasts.bestiary.harvest.HarvestGate
                .effectiveChance(0.4f, -1.0f), 0.0001f);
    }

    @Test
    void handlingIsMetAsFurtherStudyAndStopsAtTheTopTier() {
        var unknown = at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.UNKNOWN;
        var known = at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.KNOWN;
        assertEquals(at.koopro.wizardsandbeasts.bestiary.DiscoveryTier.ENCOUNTERED,
                at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.trustedTier(unknown, 1));
        assertEquals(unknown,
                at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.trustedTier(unknown, 0));
        assertEquals(known,
                at.koopro.wizardsandbeasts.creature.wildlife.WildlifeRules.trustedTier(known, 3),
                "trust cannot carry a handler past the top tier");
    }

    @Test
    void discretionNarrowsTheCircleButNeverEmptiesTheRoom() {
        double open = at.koopro.wizardsandbeasts.ministry.trace.TraceRules.witnessRadius(2);
        assertEquals(open, at.koopro.wizardsandbeasts.ministry.trace.TraceRules
                .witnessRadius(2, 0.0f), 0.0001);
        assertEquals(open * 0.75, at.koopro.wizardsandbeasts.ministry.trace.TraceRules
                .witnessRadius(2, 0.25f), 0.0001);
        // Clamped: no amount of study makes a cast unnoticeable.
        double floor = open * (1.0 - at.koopro.wizardsandbeasts.ministry.trace.TraceRules.MAX_DISCRETION);
        assertEquals(floor, at.koopro.wizardsandbeasts.ministry.trace.TraceRules
                .witnessRadius(2, 5.0f), 0.0001);
        assertTrue(floor > 0.0, "a discreet caster is still seen by anyone close enough");
    }

    @Test
    void allegianceGripAddsDuelsBeforeAWandChangesHands() {
        // The pure half of Wandlore's allegiance line: the service adds the grip to this figure, so what is
        // checked here is that an extra required win really does leave the wand where it was.
        UUID victor = UUID.randomUUID();
        WandBondHistory history = WandBondHistory.EMPTY;

        WandAllegianceRules.DefeatOutcome withoutGrip = WandAllegianceRules.defeat(history, victor, 1);
        assertTrue(withoutGrip.transferred(), "one win against a one-win wand should take it");

        WandAllegianceRules.DefeatOutcome withGrip = WandAllegianceRules.defeat(history, victor, 2);
        assertFalse(withGrip.transferred(), "a trained wizard's wand must survive the first defeat");

        WandAllegianceRules.DefeatOutcome secondDefeat =
                WandAllegianceRules.defeat(withGrip.history(), victor, 2);
        assertTrue(secondDefeat.transferred(), "losing repeatedly still loses the wand");
    }
}
