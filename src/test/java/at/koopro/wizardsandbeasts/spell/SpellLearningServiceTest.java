package at.koopro.wizardsandbeasts.spell;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.SpellCategory;
import at.koopro.wizardsandbeasts.spell.core.SpellRequirement;
import at.koopro.wizardsandbeasts.spell.core.SpellProperties;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningEligibility;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.heritage.obscurial.ObscurialRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gates, now that nothing sells a spell.
 *
 * <p>These used to be written against {@code buildOffers} — the teacher's catalogue — which is gone
 * along with the vendor. The gates it filtered on are not: they moved nowhere, and every one of them
 * still has to refuse through {@code validateLearnAttempt}, which is what a spell source reads
 * before it opens its reading channel and again before it teaches.
 */
class SpellLearningServiceTest {

    @Test
    void validateLearnAttempt_rejectsAlreadyKnownSpell() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell(Spells.PROTEGO.getId());

        SpellLearningService.LearnResult result = SpellLearningService.validateLearnAttempt(Spells.PROTEGO, data);

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
    void validateLearnAttempt_rejectsUnknownSpell() {
        SpellLearningService.LearnResult result =
                SpellLearningService.validateLearnAttempt(null, new PlayerSpellData());

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("unknown"));
    }

    /**
     * A source is only as good as the gate behind it. A book with Obscurus Surge written in it is a
     * legal item — the loot roller skips those pools, but a command or a pack can still make one —
     * and reading it has to be refused by the same rule that refused it in the catalogue.
     */
    @Test
    void validateLearnAttempt_rejectsObscurialAbilities() {
        PlayerSpellData data = new PlayerSpellData();

        SpellLearningService.LearnResult surge =
                SpellLearningService.validateLearnAttempt(Spells.OBSCURUS_SURGE, data);
        SpellLearningService.LearnResult grasp =
                SpellLearningService.validateLearnAttempt(Spells.OBSCURUS_GRASP, data);

        assertFalse(surge.success());
        assertFalse(grasp.success());
    }

    /**
     * Every refusal the service reports is the eligibility layer's own wording, verbatim. The source
     * item shows that string to the player, so a refusal invented here rather than passed through
     * would be a second copy of the rules to keep in step with the first.
     */
    @Test
    void validateLearnAttempt_passesEligibilityReasonThrough() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell(Spells.PROTEGO.getId());

        SpellLearningEligibility.Result eligibility =
                SpellLearningEligibility.evaluate(null, Spells.PROTEGO, data, null);
        SpellLearningService.LearnResult result =
                SpellLearningService.validateLearnAttempt(Spells.PROTEGO, data);

        assertFalse(eligibility.learnable());
        assertEquals(eligibility.reason(), result.message());
    }

    /**
     * A spell nobody has written yet cannot be taught by a book that names it.
     *
     * <p>The gate that most needs a source path to keep asking. The corpus registers 128 spells as
     * {@code COMING_SOON}, and a pack — or a loot pool with a typo in it — can put any of their ids on
     * a page. Under the vendor these were simply listed as locked; there is no list now, so the only
     * thing standing between a player and a spell with no implementation is this refusal.
     */
    @Test
    void validateLearnAttempt_rejectsUnimplementedSpell() {
        Spell unwritten = new TestUnimplementedSpell();
        unwritten.init();

        SpellLearningService.LearnResult result =
                SpellLearningService.validateLearnAttempt(unwritten, new PlayerSpellData());

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("still being written"));
    }

    /**
     * With the DARK_ARTS module off — which is the default, and what a bare test sees — a Dark Art is
     * unlearnable however it is presented.
     */
    @Test
    void validateLearnAttempt_rejectsDarkArtsWhenTheModuleIsOff() {
        Spell dark = new TestDarkArtSpell();
        dark.init();

        SpellLearningService.LearnResult result =
                SpellLearningService.validateLearnAttempt(dark, new PlayerSpellData());

        assertFalse(result.success());
        assertTrue(result.message().toLowerCase().contains("sealed away"));
    }

    /** A mastery-gated spell refuses until the source spell has actually reached the tier. */
    @Test
    void validateLearnAttempt_rejectsUnreachedMasteryTier() {
        Spell advanced = new TestMasteryGatedSpell();
        advanced.init();
        PlayerSpellData data = new PlayerSpellData();

        SpellLearningService.LearnResult locked =
                SpellLearningService.validateLearnAttempt(advanced, data);
        assertFalse(locked.success());
        assertTrue(locked.message().toLowerCase().contains("mastery"));

        // And opens once the tier is genuinely reached, so the test cannot pass because of some
        // unrelated refusal further up the chain.
        data.setSuccessfulHits(Spells.PROTEGO.getId(), 10_000);
        assertTrue(data.hasReachedMasteryTier(Spells.PROTEGO.getId(), PlayerSpellData.MasteryTier.PROFICIENT),
                "test fixture never reached PROFICIENT — the mastery assertion below would be vacuous");
        assertTrue(SpellLearningService.validateLearnAttempt(advanced, data).success());
    }

    @Test
    void obscurialAbility_isNotTypeUsableSpell() {
        assertTrue(ObscurialRules.isObscurialAbility(Spells.OBSCURUS_SURGE));
        at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData obscurial =
                new at.koopro.wizardsandbeasts.heritage.data.PlayerHeritageData();
        obscurial.setSelectedHeritage(Heritage.WIZARDKIND);
        obscurial.setCondition(at.koopro.wizardsandbeasts.heritage.ConditionOrigin.UNLEASHED);
        assertFalse(ObscurialRules.canHeritageUseSpell(obscurial, Spells.OBSCURUS_SURGE));
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
            return SpellRequirement.knows(Spells.PROTEGO);
        }
    }

    private static final class TestUnimplementedSpell extends Spell {
        private TestUnimplementedSpell() {
            super("test_unwritten", "Test Unwritten", SpellCategory.UTILITY, 20, 0f, 0xFFFFFF);
        }

        @Override
        public boolean isImplemented() {
            return false;
        }

        @Override
        protected SpellProperties buildProperties() {
            return null;
        }

        @Override
        protected SpellRequirement buildRequirement() {
            return SpellRequirement.none();
        }
    }

    private static final class TestDarkArtSpell extends Spell {
        private TestDarkArtSpell() {
            super("test_dark", "Test Dark", SpellCategory.DARK_ARTS, 20, 0f, 0xFFFFFF);
        }

        @Override
        protected SpellProperties buildProperties() {
            return null;
        }

        @Override
        protected SpellRequirement buildRequirement() {
            return SpellRequirement.none();
        }
    }

    private static final class TestMasteryGatedSpell extends Spell {
        private TestMasteryGatedSpell() {
            super("test_mastery", "Test Mastery", SpellCategory.UTILITY, 20, 0f, 0xFFFFFF);
        }

        @Override
        public String getMasterySourceSpellId() {
            return Spells.PROTEGO.getId();
        }

        @Override
        public PlayerSpellData.MasteryTier getRequiredMasteryTier() {
            return PlayerSpellData.MasteryTier.PROFICIENT;
        }

        @Override
        protected SpellProperties buildProperties() {
            return null;
        }

        @Override
        protected SpellRequirement buildRequirement() {
            return SpellRequirement.none();
        }
    }
}
