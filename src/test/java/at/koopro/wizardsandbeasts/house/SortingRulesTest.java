package at.koopro.wizardsandbeasts.house;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hat reads what a wizard has studied, honours a plea unless it is sure, and is the same Hat every time. */
class SortingRulesTest {

    private static final UUID SOMEONE = UUID.fromString("6b2ee645-97fa-40fe-abf9-81e17d76b228");

    @Test
    void studyDecidesTheHouse() {
        assertEquals(House.SLYTHERIN, SortingRules.ranked(SortingRules.lean(SOMEONE, Map.of("dark_arts", 6), "", 0f))[0]);
        assertEquals(House.HUFFLEPUFF, SortingRules.ranked(SortingRules.lean(SOMEONE, Map.of("herbology", 6), "", 0f))[0]);
        assertEquals(House.RAVENCLAW, SortingRules.ranked(SortingRules.lean(SOMEONE, Map.of("alchemy", 5, "wandlore", 3), "", 0f))[0]);
        assertEquals(House.GRYFFINDOR, SortingRules.ranked(SortingRules.lean(SOMEONE, Map.of("spell_mastery", 6), "", 0f))[0]);
    }

    @Test
    void corruptionLeansSlytherin() {
        assertEquals(House.SLYTHERIN, SortingRules.ranked(SortingRules.lean(SOMEONE, Map.of(), "", 100f))[0]);
    }

    @Test
    void aPleaIsHonouredUnlessTheHatIsSure() {
        Map<House, Float> close = new EnumMap<>(Map.of(House.SLYTHERIN, 6f, House.GRYFFINDOR, 4f,
                House.HUFFLEPUFF, 1f, House.RAVENCLAW, 1f));
        assertEquals(House.SLYTHERIN, SortingRules.decide(close, false));
        assertEquals(House.GRYFFINDOR, SortingRules.decide(close, true), "Not Slytherin, eh?");

        Map<House, Float> sure = new EnumMap<>(Map.of(House.SLYTHERIN, 30f, House.GRYFFINDOR, 4f,
                House.HUFFLEPUFF, 1f, House.RAVENCLAW, 1f));
        assertEquals(House.SLYTHERIN, SortingRules.decide(sure, true), "an overwhelming lean ignores the plea");
    }

    @Test
    void personalLeanIsSmallAndStable() {
        Map<House, Float> a = SortingRules.personal(SOMEONE);
        assertEquals(a, SortingRules.personal(SOMEONE));
        a.values().forEach(v -> assertTrue(v >= 0f && v <= 1.5f));
        assertNotEquals(a, SortingRules.personal(UUID.fromString("00000000-0000-0000-0000-00000000ffff")));
    }
}
