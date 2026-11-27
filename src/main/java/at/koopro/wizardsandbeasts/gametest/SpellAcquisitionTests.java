package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceOutcome;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * How spells are acquired: from books only (remake, owner 2026-11-27). No starter charm; a textbook is read through
 * and studied chapter by chapter; the skill web gives a knack for a spell, never the spell.
 */
public final class SpellAcquisitionTests {

    private static final String LUMOS = "wizards_and_beasts:lumos";
    private static final String NOX = "wizards_and_beasts:nox";

    private SpellAcquisitionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_acquisition_no_starter_charm",
                "spells: a new witch or wizard knows no spell until they read one; Lumos is not given with the wand hand",
                SpellAcquisitionTests::noStarterCharm);
        tests.add("spell_acquisition_textbook_chapter_teaches",
                "spells: The Dark Forces read through lists its chapters; studying Lumos opens its trace, a pass teaches "
                        + "Lumos and Nox, puts Lumos on the empty wheel and keeps the book; a spell the book does not "
                        + "hold cannot be studied from it",
                SpellAcquisitionTests::textbookChapterTeaches);
        tests.add("spell_acquisition_books_to_copy_and_first_lessons",
                "spells: blank spellbooks can be crafted and are sold at Ollivanders; First Lessons is earned by "
                        + "learning from a book, not by picking one up",
                SpellAcquisitionTests::blanksAndFirstLessons);
        tests.add("spell_acquisition_trace_needs_a_read",
                "tracing: asking for a book's trace without having read it is refused; after a read it opens, and "
                        + "Try again works while the read is fresh",
                SpellAcquisitionTests::traceNeedsARead);
        tests.add("spell_acquisition_cancel_costs_the_wait",
                "tracing: closing an advanced trace costs its retry wait, the wait is saved on the player, and the "
                        + "Steady hand setting is the server's and cannot change mid-trace",
                SpellAcquisitionTests::cancelCostsTheWait);
        tests.add("spell_acquisition_refusals_and_wandless",
                "spells: refusals are translatable sentences, not English strings; a goblin, who cannot use a wand, "
                        + "cannot learn a spell from a book",
                SpellAcquisitionTests::refusalsAndWandless);
        tests.add("spell_acquisition_web_gives_aptitude_not_spells",
                "spells: allocating a spell node teaches nothing and gives a knack for the spell; a respec forgets no "
                        + "spell the old web once taught (grandfathered) and clears the old ledger",
                SpellAcquisitionTests::webGivesAptitude);
    }

    private static void refusalsAndWandless(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "KnowsItAlready");
        ServerPlayer goblin = player(helper, "GoblinReader");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
            var stupefy = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:stupefy");
            wizard.getData(ModAttachments.SPELL_DATA.get()).learnSpell(stupefy.getId());
            var known = SpellLearningService.validateLearnAttempt(wizard, stupefy);
            check(helper, !known.success() && known.text().getContents()
                            instanceof net.minecraft.network.chat.contents.TranslatableContents t
                            && t.getKey().endsWith(".refusal.known"),
                    () -> "a known spell's refusal is not a translatable sentence: " + known.text());

            goblin.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(stupefy.getId());
            var wandless = SpellLearningService.validateLearnAttempt(goblin, stupefy);
            check(helper, !wandless.success() && wandless.text().getContents()
                            instanceof net.minecraft.network.chat.contents.TranslatableContents t
                            && t.getKey().endsWith(".refusal.no_wand"),
                    () -> "a goblin was not refused for having no wand hand: " + wandless.message());
            helper.succeed();
        } finally {
            retire(helper, wizard);
            retire(helper, goblin);
        }
    }

    private static void webGivesAptitude(GameTestHelper helper) {
        ServerPlayer student = player(helper, "WebAptitude");
        try {
            var spells = student.getData(ModAttachments.SPELL_DATA.get());
            spells.forgetSpell(LUMOS);
            spells.forgetSpell(NOX);
            at.koopro.wizardsandbeasts.skill.SkillSystemAPI.forceUnlock(student, "lumos_unlock");
            check(helper, !spells.knowsSpell(LUMOS), () -> "the Lumos node taught Lumos");
            check(helper, SpellTraceService.active(student) == null, () -> "the Lumos node opened a lesson");
            check(helper, at.koopro.wizardsandbeasts.skill.SkillSystemAPI.hasSpellAptitude(student, LUMOS),
                    () -> "the Lumos node gave no knack for Lumos");

            String stupefy = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:stupefy").getId();
            SpellLearningService.grantWithoutTrace(student, at.koopro.wizardsandbeasts.spell.core.Spells.byId(stupefy));
            var skills = student.getData(ModAttachments.SKILL_DATA.get());
            skills.recordWebTaughtSpell(stupefy); // as an old save's web ledger would hold it
            at.koopro.wizardsandbeasts.skill.SkillSystemAPI.getSkillData(student).setSkillLevel("lumos_unlock", 0);
            int forgotten = at.koopro.wizardsandbeasts.skill.SkillSystemAPI.revokeWebTaughtSpells(student);
            check(helper, forgotten == 0 && spells.knowsSpell(stupefy),
                    () -> "a respec took back a spell the old web had taught");
            check(helper, skills.getWebTaughtSpells().isEmpty(), () -> "the old web ledger was not cleared");
            helper.succeed();
        } finally {
            retire(helper, student);
        }
    }

    private static void textbookChapterTeaches(GameTestHelper helper) {
        ServerPlayer reader = player(helper, "TextbookReader");
        try {
            reader.getAbilities().instabuild = false;
            HeritageAPI.commit(reader, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            PlayerSpellData spells = reader.getData(ModAttachments.SPELL_DATA.get());
            spells.forgetSpell(LUMOS);
            spells.forgetSpell(NOX);
            for (int slot = 0; slot < PlayerSpellData.LOADOUT_SIZE; slot++) {
                spells.setLoadoutSpell(slot, null);
            }
            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            var book = at.koopro.wizardsandbeasts.spell.learning.book.SpellBookStacks.of("the_dark_forces");
            reader.setItemInHand(hand, book);
            var chapters = at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.chapters(reader, book);
            check(helper, chapters.size() == 3, () -> "The Dark Forces lists " + chapters.size() + " chapters, not three");

            at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.studyChapter(reader, hand, LUMOS);
            check(helper, SpellTraceService.active(reader) == null, () -> "a chapter was studied without reading the book");

            book.getItem().use(helper.getLevel(), reader, hand);
            book.getItem().finishUsingItem(book, helper.getLevel(), reader);
            check(helper, SpellTraceService.active(reader) == null, () -> "reading a textbook opened a trace by itself");
            String stupefy = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:stupefy").getId();
            at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.studyChapter(reader, hand, stupefy);
            check(helper, SpellTraceService.active(reader) == null, () -> "a spell the book does not hold was studied from it");

            at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.studyChapter(reader, hand, LUMOS);
            var outcome = TraceTestSupport.pass(reader);
            check(helper, outcome == TraceOutcome.PASSED, () -> "the Lumos chapter's trace did not pass: " + outcome);
            check(helper, spells.knowsSpell(LUMOS) && spells.knowsSpell(NOX), () -> "the chapter did not teach Lumos and Nox");
            check(helper, LUMOS.equals(spells.getActiveSpellId()), () -> "Lumos is not on the empty wheel, selected");
            check(helper, reader.getMainHandItem().getCount() == 1
                            && at.koopro.wizardsandbeasts.spell.learning.SpellSource.bookIdOf(reader.getMainHandItem()) != null,
                    () -> "the textbook was spent by a pass");
            helper.succeed();
        } finally {
            SpellTraceService.cancel(reader);
            retire(helper, reader);
        }
    }

    private static void traceNeedsARead(GameTestHelper helper) {
        ServerPlayer reader = player(helper, "UnreadBook");
        try {
            reader.getAbilities().instabuild = false;
            var spell = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:stupefy");
            reader.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(spell.getId());
            var book = new net.minecraft.world.item.ItemStack(
                    at.koopro.wizardsandbeasts.registry.CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get());
            at.koopro.wizardsandbeasts.spell.learning.SpellSource.write(book, spell);
            reader.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, book);

            SpellTraceService.request(reader, spell.getId());
            check(helper, SpellTraceService.active(reader) == null, () -> "a trace opened without the book being read");

            book.getItem().use(helper.getLevel(), reader, net.minecraft.world.InteractionHand.MAIN_HAND);
            book.getItem().finishUsingItem(book, helper.getLevel(), reader);
            var first = SpellTraceService.active(reader);
            check(helper, first != null, () -> "reading the book opened no trace");
            SpellTraceService.submit(reader, first.id(), false, new double[0]);
            SpellTraceService.request(reader, spell.getId());
            check(helper, SpellTraceService.active(reader) != null, () -> "Try again failed while the read was fresh");
            helper.succeed();
        } finally {
            SpellTraceService.cancel(reader);
            retire(helper, reader);
        }
    }

    private static void cancelCostsTheWait(GameTestHelper helper) {
        ServerPlayer student = player(helper, "CancelledTrace");
        try {
            // Bombarda asks for a proficient Diffindo, which makes its gesture ADVANCED, with a retry wait.
            String bombarda = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:bombarda").getId();
            String diffindo = at.koopro.wizardsandbeasts.spell.core.Spells.byId("wizards_and_beasts:diffindo").getId();
            var data = student.getData(ModAttachments.SPELL_DATA.get());
            data.forgetSpell(bombarda);
            data.learnSpell(diffindo);
            data.setSuccessfulHits(diffindo, 500);
            SpellTraceService.setSteadyHand(student, true);
            check(helper, SpellTraceService.steadyHand(student), () -> "the Steady hand setting was not kept");

            var hand = net.minecraft.world.InteractionHand.MAIN_HAND;
            var book = at.koopro.wizardsandbeasts.spell.learning.book.SpellBookStacks.of("standard_book_of_spells_grade_3");
            student.setItemInHand(hand, book);
            book.getItem().finishUsingItem(book, helper.getLevel(), student);
            at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.studyChapter(student, hand, bombarda);
            var challenge = SpellTraceService.active(student);
            check(helper, challenge != null && challenge.difficulty().retryDelayTicks() > 0,
                    () -> "setup: no advanced Bombarda trace opened: " + challenge);
            SpellTraceService.setSteadyHand(student, false);
            check(helper, SpellTraceService.steadyHand(student), () -> "Steady hand changed in the middle of a trace");

            var outcome = SpellTraceService.submit(student, challenge.id(), false, new double[0]);
            check(helper, outcome == TraceOutcome.CANCELLED, () -> "expected CANCELLED, got " + outcome);
            long now = student.level().getGameTime();
            check(helper, SpellTraceService.retryWait(student, bombarda, now) > 0,
                    () -> "closing the trace cost nothing: a free reroll");
            check(helper, student.getData(ModAttachments.TRACE_MEMORY.get()).waitFor(bombarda, now) > 0,
                    () -> "the retry wait is not saved on the player, so a relog would clear it");
            at.koopro.wizardsandbeasts.spell.learning.book.SpellBookService.studyChapter(student, hand, bombarda);
            check(helper, SpellTraceService.active(student) == null,
                    () -> "the trace re-opened inside its retry wait");
            helper.succeed();
        } finally {
            SpellTraceService.cancel(student);
            retire(helper, student);
        }
    }

    private static void blanksAndFirstLessons(GameTestHelper helper) {
        ServerPlayer reader = player(helper, "FirstReader");
        try {
            var server = helper.getLevel().getServer();
            boolean recipe = server.getRecipeManager().getRecipes().stream()
                    .anyMatch(h -> h.id().identifier().getPath().equals("blank_book_of_spells"));
            check(helper, recipe, () -> "the blank spellbook recipe did not load");
            var ollivanders = at.koopro.wizardsandbeasts.location.Locations.get(
                    net.minecraft.resources.Identifier.fromNamespaceAndPath("wizards_and_beasts", "ollivanders"));
            check(helper, ollivanders != null && ollivanders.market().isPresent()
                            && ollivanders.market().get().sells().stream().anyMatch(g -> g.id().equals("blank_book_of_spells")),
                    () -> "Ollivanders sells no blank spellbooks");

            HeritageAPI.commit(reader, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            var holder = server.getAdvancements().get(net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    "wizards_and_beasts", "progression/spell_teacher"));
            check(helper, holder != null, () -> "setup: First Lessons is not loaded");
            reader.getInventory().add(new net.minecraft.world.item.ItemStack(
                    at.koopro.wizardsandbeasts.registry.CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get()));
            check(helper, !reader.getAdvancements().getOrStartProgress(holder).isDone(),
                    () -> "First Lessons was earned by picking up a book");
            reader.getData(ModAttachments.SPELL_DATA.get()).forgetSpell("wizards_and_beasts:stupefy");
            var result = SpellLearningService.tryLearnSpell(reader, "wizards_and_beasts:stupefy");
            check(helper, result.success(), () -> "setup: Stupefy was not learned: " + result);
            check(helper, reader.getAdvancements().getOrStartProgress(holder).isDone(),
                    () -> "learning from a book did not earn First Lessons");
            helper.succeed();
        } finally {
            retire(helper, reader);
        }
    }

    private static void noStarterCharm(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "NoFirstLight");
        try {
            PlayerSpellData spells = wizard.getData(ModAttachments.SPELL_DATA.get());
            spells.forgetSpell(LUMOS);
            spells.forgetSpell(NOX);
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            check(helper, !spells.knowsSpell(LUMOS) && !spells.knowsSpell(NOX),
                    () -> "choosing a wand-holding heritage taught Lumos without a book");
            helper.succeed();
        } finally {
            retire(helper, wizard);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
