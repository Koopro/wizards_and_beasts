package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the prerequisite + proficiency matrix for {@link SpellRequirement}.
 * Server-side casting now consults this when {@code Config.enforceSpellRequirements}
 * is enabled, so the matrix below is part of the gameplay contract.
 */
class SpellRequirementTest {

    // Note: we deliberately do NOT call Spells.init() here. init() invokes
    // buildProperties() on every spell, which references vanilla SoundEvents
    // and other registries that require Minecraft bootstrap. The Spells.X
    // constants are populated by class-load-time constructors that only set
    // primitive metadata (id, displayName, category, cooldown, damage, color),
    // so SpellRequirement factory methods work without bootstrap.

    @Test
    void none_alwaysMet() {
        PlayerSpellData empty = new PlayerSpellData();
        assertTrue(SpellRequirement.none().isMet(empty));
        assertSame(SpellRequirement.NONE, SpellRequirement.none(),
                "none() must return the shared NONE instance.");
        assertNull(SpellRequirement.NONE.getPrerequisiteId());
        assertNull(SpellRequirement.NONE.getMinProficiency());
    }

    @Test
    void knows_requiresPrerequisiteSpell() {
        SpellRequirement req = SpellRequirement.knows(Spells.EXPELLIARMUS);
        PlayerSpellData data = new PlayerSpellData();

        assertFalse(req.isMet(data), "Player who has not learned Expelliarmus must not satisfy knows(Expelliarmus).");

        data.learnSpell("expelliarmus");
        assertTrue(req.isMet(data), "After learning Expelliarmus the requirement must be satisfied.");
        assertEquals("expelliarmus", req.getPrerequisiteId());
        assertNull(req.getMinProficiency(), "knows(...) must not impose a proficiency floor.");
    }

    @Test
    void proficiency_requiresKnowledgeAndSuccessfulHits() {
        SpellRequirement req = SpellRequirement.proficiency(Spells.EXPELLIARMUS, Proficiency.PROFICIENT);
        PlayerSpellData data = new PlayerSpellData();

        assertFalse(req.isMet(data), "No knowledge -> not met.");

        data.learnSpell("expelliarmus");
        assertFalse(req.isMet(data),
                "Learning the spell alone is not enough; needs PROFICIENT successful hits.");

        for (int i = 0; i < Proficiency.PROFICIENT.getCastsRequired() - 1; i++) {
            data.incrementSuccessfulHits("expelliarmus");
        }
        assertFalse(req.isMet(data),
                "Just below the PROFICIENT threshold must not satisfy the requirement.");

        data.incrementSuccessfulHits("expelliarmus");
        assertTrue(req.isMet(data),
                "Reaching the PROFICIENT successful-hit threshold must satisfy the requirement.");
    }

    @Test
    void proficiency_masteredImpliesProficient() {
        SpellRequirement req = SpellRequirement.proficiency(Spells.EXPELLIARMUS, Proficiency.MASTERED);
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell("expelliarmus");
        for (int i = 0; i < Proficiency.MASTERED.getCastsRequired(); i++) {
            data.incrementSuccessfulHits("expelliarmus");
        }
        assertTrue(req.isMet(data));
    }

    @Test
    void description_includesPrerequisiteName() {
        String knowsDesc = SpellRequirement.knows(Spells.EXPELLIARMUS).getDescription();
        assertTrue(knowsDesc.contains("Expelliarmus"),
                "knows(Expelliarmus) description should mention Expelliarmus. Got: " + knowsDesc);

        String profDesc = SpellRequirement
                .proficiency(Spells.EXPELLIARMUS, Proficiency.MASTERED)
                .getDescription();
        assertTrue(profDesc.toLowerCase().contains("master"),
                "proficiency(MASTERED) description should mention mastery. Got: " + profDesc);
        assertTrue(profDesc.contains("Expelliarmus"),
                "proficiency(Expelliarmus) description should mention Expelliarmus. Got: " + profDesc);
    }
}
