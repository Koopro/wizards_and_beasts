package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.corruption.DarkCorruptionService;
import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.module.Module;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.learning.SpellLawLearningGate;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Wizarding law reaches the books, not only the courtroom.
 *
 * <p>{@code spell_law} has classified spells since the Ministry layer landed and nothing on the
 * learning path ever read it, so a first-year who found the right book could read the Killing Curse
 * out of it as easily as Lumos. The classification was decorative.
 *
 * <p>What these scenarios pin is the shape of the rule rather than its numbers. Dark magic is not
 * forbidden to learn — plenty of people in the books learned things they should not have — but an
 * Unforgivable is not something anyone stumbles into, so the gate asks for study the skill web
 * already charges for, and it asks in proportion to what the law says about the spell.
 */
public final class SpellLawLearningTests {

    private SpellLawLearningTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_law_ordinary_magic_is_freely_learnable",
                "spell law: an unrestricted spell is taught with no extra friction",
                SpellLawLearningTests::ordinaryMagicIsFree);
        tests.add("spell_law_unforgivable_refused_without_study",
                "spell law: a book will not teach an Unforgivable to an untrained student",
                SpellLawLearningTests::unforgivableRefusedWithoutStudy);
        tests.add("spell_law_unforgivable_opens_after_curse_control",
                "spell law: the same book teaches once the study behind it is done",
                SpellLawLearningTests::unforgivableOpensAfterStudy);
        tests.add("spell_law_inspecting_a_book_costs_nothing",
                "spell law: a refused read leaves the reader unstained",
                SpellLawLearningTests::refusedReadCostsNothing);
    }

    // ── scenarios ───────────────────────────────────────────────────────────────────────────────

    /** Lumos is Lumos. The gate must not have made ordinary magic harder to come by. */
    private static void ordinaryMagicIsFree(GameTestHelper helper) {
        ServerPlayer student = student(helper, "LawStudent");
        try {
            Spell lumos = Spells.byId("lumos");
            WizardTestSupport.check(helper, lumos != null, () -> "lumos is not registered");

            SpellLearningService.LearnResult result =
                    SpellLearningService.validateLearnAttempt(student, lumos);
            WizardTestSupport.check(helper, result.success(),
                    () -> "an unrestricted spell was refused: " + result.message());

            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, student);
        }
    }

    /**
     * The contradiction this whole change exists to remove: no study, no Unforgivable, however good
     * the book is.
     */
    private static void unforgivableRefusedWithoutStudy(GameTestHelper helper) {
        ServerPlayer student = student(helper, "UntrainedStudent");
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        try {
            Spell crucio = Spells.byId("crucio");
            WizardTestSupport.check(helper, crucio != null, () -> "crucio is not registered");

            SpellLearningService.LearnResult result =
                    SpellLearningService.tryLearnSpell(student, "crucio");
            WizardTestSupport.check(helper, !result.success(),
                    () -> "an untrained student learned an Unforgivable from a book");
            WizardTestSupport.check(helper,
                    !WizardTestSupport.spellData(student).knowsSpell(crucio.getId()),
                    () -> "the refusal was reported but the spell was learned anyway");

            helper.succeed();
        } finally {
            darkArts.run();
            WizardTestSupport.retire(helper, student);
        }
    }

    /**
     * The other half, and the reason this is a gate rather than a ban: once the study behind it is
     * done, the same book teaches. The skill web already puts every Unforgivable node behind
     * {@code curse_control}; this makes a book ask for the same thing rather than route around it.
     */
    private static void unforgivableOpensAfterStudy(GameTestHelper helper) {
        ServerPlayer student = student(helper, "TrainedStudent");
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        try {
            Spell crucio = Spells.byId("crucio");

            // Before the study, the law itself is what stands in the way.
            WizardTestSupport.check(helper,
                    SpellLawLearningGate.refusal(student, crucio) != null,
                    () -> "the law raised no objection before any study was done");

            SkillSystemAPI.forceUnlock(student, SpellLawLearningGate.STUDY_DARK_ARTS);
            SkillSystemAPI.forceUnlock(student, SpellLawLearningGate.STUDY_CURSE_CONTROL);

            // After it, the law is satisfied. Asserted against the gate rather than against the whole
            // learn attempt on purpose: Crucio also carries an authored proficiency requirement
            // ("Mastered in Incendio") that has nothing to do with legality, and asserting on the
            // combined result would make this scenario a test of that requirement instead of this one.
            WizardTestSupport.check(helper,
                    SpellLawLearningGate.refusal(student, crucio) == null,
                    () -> "a student who has studied curse control is still refused by the law");

            helper.succeed();
        } finally {
            darkArts.run();
            WizardTestSupport.retire(helper, student);
        }
    }

    /**
     * Picking a book up and being told no is not studying it. The stain is charged where the spell is
     * actually learned, never from the preview {@code SpellSourceItem} runs on the first right-click.
     */
    private static void refusedReadCostsNothing(GameTestHelper helper) {
        ServerPlayer student = student(helper, "CuriousStudent");
        Runnable darkArts = WizardTestSupport.leaseModule(Module.DARK_ARTS);
        try {
            float before = DarkCorruptionService.get(student);

            Spell crucio = Spells.byId("crucio");
            SpellLearningService.validateLearnAttempt(student, crucio);
            SpellLearningService.tryLearnSpell(student, "crucio");

            float after = DarkCorruptionService.get(student);
            WizardTestSupport.check(helper, after == before,
                    () -> "a refused read stained the reader: " + before + " -> " + after);

            helper.succeed();
        } finally {
            darkArts.run();
            WizardTestSupport.retire(helper, student);
        }
    }

    private static ServerPlayer student(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        HeritageAPI.commit(player, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
        return player;
    }
}
