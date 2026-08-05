package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.spell.core.Proficiency;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        SpellRequirement req = SpellRequirement.knows(Spells.PROTEGO);
        PlayerSpellData data = new PlayerSpellData();

        assertFalse(req.isMet(data), "Player who has not learned Protego must not satisfy knows(Protego).");

        data.learnSpell("protego");
        assertTrue(req.isMet(data), "After learning Protego the requirement must be satisfied.");
        assertEquals("protego", req.getPrerequisiteId());
        assertNull(req.getMinProficiency(), "knows(...) must not impose a proficiency floor.");
    }

    @Test
    void proficiency_requiresKnowledgeAndSuccessfulHits() {
        SpellRequirement req = SpellRequirement.proficiency(Spells.PROTEGO, Proficiency.PROFICIENT);
        PlayerSpellData data = new PlayerSpellData();

        assertFalse(req.isMet(data), "No knowledge -> not met.");

        data.learnSpell("protego");
        assertFalse(req.isMet(data),
                "Learning the spell alone is not enough; needs PROFICIENT successful hits.");

        for (int i = 0; i < Proficiency.PROFICIENT.getCastsRequired() - 1; i++) {
            data.incrementSuccessfulHits("protego");
        }
        assertFalse(req.isMet(data),
                "Just below the PROFICIENT threshold must not satisfy the requirement.");

        data.incrementSuccessfulHits("protego");
        assertTrue(req.isMet(data),
                "Reaching the PROFICIENT successful-hit threshold must satisfy the requirement.");
    }

    @Test
    void proficiency_masteredImpliesProficient() {
        SpellRequirement req = SpellRequirement.proficiency(Spells.PROTEGO, Proficiency.MASTERED);
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell("protego");
        for (int i = 0; i < Proficiency.MASTERED.getCastsRequired(); i++) {
            data.incrementSuccessfulHits("protego");
        }
        assertTrue(req.isMet(data));
    }

    @Test
    void description_includesPrerequisiteName() {
        // `describe()` returns a translatable Component rather than an English sentence, so
        // this asserts the contract that survives translation: the right key, and the
        // prerequisite carried as an argument. Asserting on rendered text would only pass
        // in English, and under a unit test with no language loaded would not pass at all.
        var knows = SpellRequirement.knows(Spells.PROTEGO).describe().getContents();
        assertInstanceOf(TranslatableContents.class, knows);
        TranslatableContents kc = (TranslatableContents) knows;
        assertEquals("spell.wizards_and_beasts.req.requires", kc.getKey());
        assertTrue(argsToString(kc).contains("protego"),
                "knows(Protego) should carry Protego as an argument. Got: " + argsToString(kc));

        var prof = SpellRequirement
                .proficiency(Spells.PROTEGO, Proficiency.MASTERED)
                .describe().getContents();
        assertInstanceOf(TranslatableContents.class, prof);
        TranslatableContents pc = (TranslatableContents) prof;
        assertEquals("spell.wizards_and_beasts.req.proficiency", pc.getKey());
        String args = argsToString(pc);
        assertTrue(args.contains("mastered"),
                "proficiency(MASTERED) should carry the mastery tier. Got: " + args);
        assertTrue(args.contains("protego"),
                "proficiency(Protego) should carry Protego. Got: " + args);
    }

    /**
     * Flattens a translatable component's arguments, including nested keys, to one lowercase
     * string.
     *
     * <p>Lowercased because the two sources disagree on case: a datapack spell's name is now
     * a key (`spell.wizards_and_beasts.protego.name`) while the hardcoded `Spells.PROTEGO`
     * constant still carries the literal "Protego". This asserts the prerequisite is carried,
     * not which of the two forms it took.
     */
    private static String argsToString(TranslatableContents c) {
        StringBuilder sb = new StringBuilder();
        for (Object a : c.getArgs()) {
            if (a instanceof net.minecraft.network.chat.Component comp
                    && comp.getContents() instanceof TranslatableContents nested) {
                sb.append(nested.getKey()).append(' ');
            } else {
                sb.append(a).append(' ');
            }
        }
        return sb.toString().toLowerCase(java.util.Locale.ROOT);
    }
}
