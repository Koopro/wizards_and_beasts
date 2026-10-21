package at.koopro.wizardsandbeasts.heritage.profession;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.skill.GameplayStat;
import at.koopro.wizardsandbeasts.skill.SkillEffect.GameplayBonus;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Heritage trades pay in their own currency, never in the old shared stat formula. */
class ProfessionNodeTest {

    @Test
    void everyTradePaysSomethingTheGameReads() {
        for (ProfessionNode node : ProfessionNode.values()) {
            List<GameplayBonus> bonuses = node.getBonuses();
            assertFalse(bonuses.isEmpty(), node.getId() + " costs points and pays nothing");
            for (GameplayBonus b : bonuses) {
                assertTrue(b.perLevel() > 0f, node.getId() + " pays a non-positive " + b.stat());
                // Fractions stay small enough that one heritage line cannot max a clamp on its own.
                if (!b.stat().isCount()) {
                    assertTrue(b.perLevel() <= 0.25f, node.getId() + " pays " + b.perLevel() + " " + b.stat());
                }
            }
        }
    }

    @Test
    void aHeritageLineIsAnIdentityNotAStatSpread() {
        for (Heritage heritage : Heritage.values()) {
            Set<GameplayStat> stats = EnumSet.noneOf(GameplayStat.class);
            for (ProfessionNode node : ProfessionNode.byHeritage(heritage)) {
                node.getBonuses().forEach(b -> stats.add(b.stat()));
            }
            assertTrue(stats.size() <= 4, heritage + " line pays in " + stats.size() + " currencies: " + stats);
        }
    }

    @Test
    void bonusesSumOverUnlockedTradesAndIgnoreUnknownIds() {
        Set<String> unlocked = Set.of("wizard_apprentice", "wizard_wandmaker", "removed_node");
        assertEquals(1.0f, ProfessionNode.bonus(unlocked, GameplayStat.ALLEGIANCE_GRIP), 1e-6f);
        assertEquals(0.03f, ProfessionNode.bonus(unlocked, GameplayStat.SPELL_MISFIRE_REDUCTION), 1e-6f);
        assertEquals(0f, ProfessionNode.bonus(unlocked, GameplayStat.WARD_LIFETIME), 1e-6f);
        assertEquals(0f, ProfessionNode.bonus(Set.of(), GameplayStat.ALLEGIANCE_GRIP), 1e-6f);
    }
}
