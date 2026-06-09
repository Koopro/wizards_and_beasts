package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellLearningServiceTest {

    @Test
    void buildOffers_excludesAlreadyKnownSpells() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell(Spells.FLIPENDO.getId());

        List<SpellLearningService.SpellOffer> offers = SpellLearningService.buildOffers(data);

        assertFalse(offers.stream().anyMatch(o -> o.spellId().equals(Spells.FLIPENDO.getId())),
                "Known spells should not appear in teacher offers.");
    }

    @Test
    void validateLearnAttempt_rejectsAlreadyKnownSpell() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell(Spells.FLIPENDO.getId());

        SpellLearningService.LearnResult result = SpellLearningService.validateLearnAttempt(Spells.FLIPENDO, data);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("already"));
    }

    @Test
    void validateLearnAttempt_rejectsUnmetRequirement() {
        PlayerSpellData data = new PlayerSpellData();
        Spell gated = new TestRequirementSpell();
        gated.init();

        SpellLearningService.LearnResult result = SpellLearningService.validateLearnAttempt(gated, data);

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("requires"));
    }

    @Test
    void buildOffers_excludesObscurialAbilities() {
        PlayerSpellData data = new PlayerSpellData();
        List<SpellLearningService.SpellOffer> offers = SpellLearningService.buildOffers(data);
        assertFalse(offers.stream().anyMatch(o -> o.spellId().equals("obscurus_surge")));
        assertFalse(offers.stream().anyMatch(o -> o.spellId().equals("obscurus_grasp")));
    }

    @Test
    void obscurialAbility_isNotTypeUsableSpell() {
        assertTrue(ObscurialRules.isObscurialAbility(Spells.OBSCURUS_SURGE));
        assertFalse(ObscurialRules.canHeritageUseSpell(Heritage.OBSCURIAL, Spells.OBSCURUS_SURGE));
    }

    private static final class TestRequirementSpell extends Spell {
        private TestRequirementSpell() {
            super("test_req", "Test Requirement", SpellCategory.UTILITY, 20, 0f, 0xFFFFFF);
        }

        @Override
        protected SpellProperties buildProperties() {
            return null;
        }

        @Override
        protected SpellRequirement buildRequirement() {
            return SpellRequirement.knows(Spells.FLIPENDO);
        }
    }
}
