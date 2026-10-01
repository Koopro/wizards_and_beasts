package at.koopro.wizardsandbeasts.heritage.rules;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The heritage rules every gate consults: shipped defaults, overrides, and the remote layer. */
class HeritageRulesTest {

    @AfterEach
    void reset() {
        HeritageRules.publishLocal(Map.of());
        HeritageRules.clearRemote();
    }

    @Test
    void withoutOverridesTheShippedFlagDecides() {
        for (Heritage heritage : Heritage.values()) {
            assertEquals(heritage.isAlphaAvailable(), HeritageRules.selectable(heritage), heritage.getId());
            assertTrue(HeritageRules.transformationAllowed(heritage), heritage.getId());
            assertEquals(heritage.isAlphaAvailable(), HeritageRules.refusalKey(heritage) == null, heritage.getId());
        }
    }

    @Test
    void anOverrideWinsInBothDirectionsAndSaysWhy() {
        Heritage open = firstWith(true);
        Heritage unfinished = firstWith(false);
        HeritageRules.publishLocal(Map.of(
                open.getId(), HeritageRule.NONE.withSelectable(Optional.of(false)),
                unfinished.getId(), HeritageRule.NONE.withSelectable(Optional.of(true))));
        assertFalse(HeritageRules.selectable(open));
        assertEquals("message.wizards_and_beasts.type_selection.closed", HeritageRules.refusalKey(open));
        assertTrue(HeritageRules.selectable(unfinished));
        assertNull(HeritageRules.refusalKey(unfinished));
    }

    @Test
    void anUnfinishedHeritageIsRefusedAsComingSoon() {
        Heritage unfinished = firstWith(false);
        assertEquals("message.wizards_and_beasts.type_selection.coming_soon", HeritageRules.refusalKey(unfinished));
    }

    @Test
    void theRemoteLayerWinsWhileSetAndLeavesWithTheServer() {
        Heritage heritage = firstWith(true);
        HeritageRules.acceptRemote(Map.of(heritage.getId(),
                new HeritageRule(Optional.of(false), Optional.of(false))));
        assertFalse(HeritageRules.selectable(heritage));
        assertFalse(HeritageRules.transformationAllowed(heritage));
        HeritageRules.clearRemote();
        assertTrue(HeritageRules.selectable(heritage));
        assertTrue(HeritageRules.transformationAllowed(heritage));
    }

    @Test
    void anEmptyRuleIsEmpty() {
        assertTrue(HeritageRule.NONE.isEmpty());
        assertFalse(HeritageRule.NONE.withTransformation(Optional.of(true)).isEmpty());
        assertTrue(HeritageRule.NONE.withTransformation(Optional.of(true)).withTransformation(Optional.empty()).isEmpty());
    }

    private static Heritage firstWith(boolean alphaAvailable) {
        for (Heritage heritage : Heritage.values()) {
            if (heritage.isAlphaAvailable() == alphaAvailable) {
                return heritage;
            }
        }
        throw new AssertionError("no heritage with isAlphaAvailable=" + alphaAvailable);
    }
}
