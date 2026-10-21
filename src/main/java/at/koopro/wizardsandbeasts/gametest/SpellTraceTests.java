package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.skill.SkillSystemAPI;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import at.koopro.wizardsandbeasts.spell.learning.tracing.SpellTraceService;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceChallenge;
import at.koopro.wizardsandbeasts.spell.learning.tracing.TraceOutcome;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/**
 * The tracing challenge's server contract: what a failed, replayed, stale, late or orphaned attempt
 * does — nothing — and what a passed one does exactly once.
 *
 * <p>Driven through {@link SpellTraceService#submit}, the same entry point the submit payload calls, so
 * these exercise the real judge, timing, source re-check and commit.
 */
public final class SpellTraceTests {

    private static final String BASIC_SPELL = "lumos";
    private static final String COMPANION = "nox";
    private static final String NODE = "frigora_unlock";
    private static final String NODE_SPELL = "frigora";

    private SpellTraceTests() {
    }

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_trace_failure_keeps_book_and_teaches_nothing",
                "tracing: leaving the lines teaches nothing and spends no page", SpellTraceTests::failureKeepsSource);
        tests.add("spell_trace_double_submit_teaches_and_spends_once",
                "tracing: a replayed submission finds no challenge; one page, one lesson", SpellTraceTests::doubleSubmit);
        tests.add("spell_trace_stale_or_foreign_id_is_ignored",
                "tracing: a wrong id, or another player's, leaves the live challenge alone", SpellTraceTests::staleAndForeign);
        tests.add("spell_trace_rejects_instant_and_late_attempts",
                "tracing: the server clock rejects a trace too fast for a hand and one past the deadline",
                SpellTraceTests::timing);
        tests.add("spell_trace_book_swapped_mid_trace_teaches_nothing",
                "tracing: a book that left the hand before the pass teaches nothing", SpellTraceTests::bookSwapped);
        tests.add("spell_trace_gates_rerun_at_commit",
                "tracing: learning the spell another way mid-trace makes the pass a refusal, page kept",
                SpellTraceTests::gatesRerun);
        tests.add("spell_trace_lumos_teaches_nox",
                "tracing: one Lumos trace teaches its companion Nox", SpellTraceTests::companion);
        tests.add("spell_trace_lesson_refunded_mid_trace_owes_nothing",
                "tracing: a node refunded during its lesson cannot be passed into a spell", SpellTraceTests::lessonRefunded);
        tests.add("spell_learning_mastery_gate_reads_canonical_hits",
                "learning: a bare masterySpellId (episkey -> reparo) counts the canonical hit ledger",
                SpellTraceTests::masteryGateCanonical);
        tests.add("spell_give_command_hands_a_written_source",
                "command: /wandb magic spell give hands an admin a written page; a non-admin gets nothing",
                SpellTraceTests::giveCommand);
        tests.add("spell_trace_death_cancels_the_challenge",
                "tracing: dying ends the challenge", SpellTraceTests::deathCancels);
    }

    private static void failureKeepsSource(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceFail");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            player.getAbilities().instabuild = false;
            ItemStack page = page(spell, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, page);
            openFromHand(helper, player, page);

            TraceOutcome outcome = TraceTestSupport.failOffPath(player);
            WizardTestSupport.check(helper, outcome == TraceOutcome.LEFT_PATH, () -> "expected LEFT_PATH, got " + outcome);
            WizardTestSupport.check(helper, !spells(player).knowsSpell(spell.getId()), () -> "a failed trace taught the spell");
            WizardTestSupport.check(helper, page.getCount() == 1, () -> "a failed trace spent the page");
            WizardTestSupport.check(helper, SpellTraceService.active(player) == null, () -> "the failed challenge lingered");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void doubleSubmit(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceTwice");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            player.getAbilities().instabuild = false;
            ItemStack page = page(spell, 3);
            player.setItemInHand(InteractionHand.MAIN_HAND, page);
            openFromHand(helper, player, page);
            TraceChallenge c = SpellTraceService.active(player);
            WizardTestSupport.check(helper, c != null, () -> "no challenge opened");

            SpellTraceService.backdateForTest(player, TraceTestSupport.HUMAN_TICKS);
            double[] xy = TraceTestSupport.centreLine(c.path());
            TraceOutcome first = SpellTraceService.submit(player, c.id(), false, xy);
            TraceOutcome second = SpellTraceService.submit(player, c.id(), false, xy);
            WizardTestSupport.check(helper, first == TraceOutcome.PASSED, () -> "first submit: " + first);
            WizardTestSupport.check(helper, second == null, () -> "a replayed submit was judged: " + second);
            WizardTestSupport.check(helper, page.getCount() == 2, () -> "pages left " + page.getCount() + ", expected 2");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void staleAndForeign(GameTestHelper helper) {
        ServerPlayer owner = WizardTestSupport.placeMockPlayer(helper, "TraceOwner");
        ServerPlayer other = WizardTestSupport.placeMockPlayer(helper, "TraceThief");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            ItemStack book = book(spell);
            owner.setItemInHand(InteractionHand.MAIN_HAND, book);
            openFromHand(helper, owner, book);
            TraceChallenge c = SpellTraceService.active(owner);
            WizardTestSupport.check(helper, c != null, () -> "no challenge opened");
            SpellTraceService.backdateForTest(owner, TraceTestSupport.HUMAN_TICKS);
            double[] xy = TraceTestSupport.centreLine(c.path());

            TraceOutcome foreign = SpellTraceService.submit(other, c.id(), false, xy);
            TraceOutcome stale = SpellTraceService.submit(owner, c.id() + 1, false, xy);
            WizardTestSupport.check(helper, foreign == null && stale == null,
                    () -> "foreign=" + foreign + " stale=" + stale + " (both should be ignored)");
            WizardTestSupport.check(helper, !spells(other).knowsSpell(spell.getId()), () -> "another player's trace taught the thief");
            TraceChallenge still = SpellTraceService.active(owner);
            WizardTestSupport.check(helper, still != null && still.id() == c.id(),
                    () -> "a stale or foreign submit consumed the live challenge");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, owner);
            WizardTestSupport.retire(helper, other);
        }
    }

    private static void timing(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceClock");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            ItemStack book = book(spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);

            openFromHand(helper, player, book);
            TraceChallenge fast = SpellTraceService.active(player);
            TraceOutcome instant = SpellTraceService.submit(player, fast.id(), false, TraceTestSupport.centreLine(fast.path()));
            WizardTestSupport.check(helper, instant == TraceOutcome.TOO_FAST, () -> "same-tick submit: " + instant);

            openFromHand(helper, player, book);
            TraceChallenge slow = SpellTraceService.active(player);
            SpellTraceService.backdateForTest(player, 20 * 120);
            TraceOutcome late = SpellTraceService.submit(player, slow.id(), false, TraceTestSupport.centreLine(slow.path()));
            WizardTestSupport.check(helper, late == TraceOutcome.TOO_SLOW, () -> "two-minute-old submit: " + late);
            WizardTestSupport.check(helper, !spells(player).knowsSpell(spell.getId()), () -> "a mistimed trace taught the spell");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void bookSwapped(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceSwap");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            ItemStack book = book(spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            openFromHand(helper, player, book);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

            TraceOutcome outcome = TraceTestSupport.pass(player);
            WizardTestSupport.check(helper, outcome == TraceOutcome.SOURCE_GONE, () -> "expected SOURCE_GONE, got " + outcome);
            WizardTestSupport.check(helper, !spells(player).knowsSpell(spell.getId()), () -> "an absent book taught the spell");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void gatesRerun(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceGate");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            player.getAbilities().instabuild = false;
            ItemStack page = page(spell, 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, page);
            openFromHand(helper, player, page);
            // Mid-trace the spell is learned some other way; "already known" is now a gate that refuses.
            spells(player).learnSpell(spell.getId());

            TraceOutcome outcome = TraceTestSupport.pass(player);
            WizardTestSupport.check(helper, outcome == TraceOutcome.REFUSED, () -> "expected REFUSED, got " + outcome);
            WizardTestSupport.check(helper, page.getCount() == 1, () -> "a refused commit spent the page");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void companion(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceLumos");
        try {
            Spell lumos = Spells.byId(BASIC_SPELL);
            Spell nox = Spells.byId(COMPANION);
            ItemStack book = book(lumos);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            openFromHand(helper, player, book);
            TraceOutcome outcome = TraceTestSupport.pass(player);
            WizardTestSupport.check(helper, outcome == TraceOutcome.PASSED, () -> "Lumos trace: " + outcome);
            WizardTestSupport.check(helper, spells(player).knowsSpell(nox.getId()), () -> "Lumos did not teach Nox");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void lessonRefunded(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceRefund");
        try {
            Spell spell = Spells.byId(NODE_SPELL);
            SkillSystemAPI.forceUnlock(player, NODE);
            WizardTestSupport.check(helper, SpellTraceService.active(player) != null, () -> "allocation opened no lesson");
            SkillSystemAPI.getSkillData(player).setSkillLevel(NODE, 0);

            TraceOutcome outcome = TraceTestSupport.pass(player);
            WizardTestSupport.check(helper, outcome == TraceOutcome.SOURCE_GONE, () -> "expected SOURCE_GONE, got " + outcome);
            WizardTestSupport.check(helper, !spells(player).knowsSpell(spell.getId()), () -> "a refunded node's lesson taught");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void deathCancels(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceDeath");
        try {
            Spell spell = Spells.byId(BASIC_SPELL);
            ItemStack book = book(spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);
            openFromHand(helper, player, book);
            player.getAbilities().invulnerable = false;
            player.kill(helper.getLevel());
            WizardTestSupport.check(helper, SpellTraceService.active(player) == null, () -> "the challenge survived death");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void masteryGateCanonical(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "TraceMastery");
        try {
            Spell episkey = Spells.byId("episkey");
            Spell reparo = Spells.byId("reparo");
            WizardTestSupport.check(helper, episkey != null && reparo != null, () -> "episkey/reparo not registered");
            spells(player).learnSpell(reparo.getId()); // episkey's own "knows reparo" requirement
            var before = at.koopro.wizardsandbeasts.spell.learning.SpellLearningService.validateLearnAttempt(player, episkey);
            WizardTestSupport.check(helper, !before.success() && before.message().contains("mastery"),
                    () -> "episkey without reparo practice: " + before.message());
            for (int i = 0; i < at.koopro.wizardsandbeasts.spell.core.Proficiency.PROFICIENT.getCastsRequired(); i++) {
                spells(player).incrementSuccessfulHits(reparo.getId());
            }
            var after = at.koopro.wizardsandbeasts.spell.learning.SpellLearningService.validateLearnAttempt(player, episkey);
            WizardTestSupport.check(helper, after.success() || !after.message().contains("mastery"),
                    () -> "practising reparo did not open the episkey mastery gate: " + after.message());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void giveCommand(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        java.util.UUID listed = AdminFrameworkTests.firstAllowListedUuid();
        ServerPlayer admin = listed == null
                ? WizardTestSupport.placeMockPlayer(helper, "GiveAdmin")
                : WizardTestSupport.placeMockPlayer(helper, "GiveAdmin", listed, net.minecraft.world.level.GameType.CREATIVE);
        ServerPlayer outsider = WizardTestSupport.placeMockPlayer(helper, "GiveOutsider",
                net.minecraft.world.level.GameType.SURVIVAL);
        boolean opped = false;
        try {
            if (listed == null) {
                server.getPlayerList().op(admin.nameAndId());
                opped = true;
            }
            // A mock player's inventory is not empty, and an allow-listed admin's reloads (lesson L862).
            admin.getInventory().clearContent();
            outsider.getInventory().clearContent();
            server.getCommands().performPrefixedCommand(outsider.createCommandSourceStack().withSuppressedOutput(),
                    "wandb magic spell give alohomora page 2");
            WizardTestSupport.check(helper, countWritten(outsider, "alohomora") == 0,
                    () -> "a non-admin gave themselves a spell page");

            server.getCommands().performPrefixedCommand(admin.createCommandSourceStack().withSuppressedOutput(),
                    "wandb magic spell give alohomora page 2");
            int pages = countWritten(admin, "alohomora");
            WizardTestSupport.check(helper, pages == 2, () -> "expected 2 written pages, found " + pages);
            helper.succeed();
        } finally {
            if (opped) {
                server.getPlayerList().deop(admin.nameAndId());
            }
            WizardTestSupport.retire(helper, admin);
            WizardTestSupport.retire(helper, outsider);
        }
    }

    private static int countWritten(ServerPlayer player, String spellId) {
        Spell spell = Spells.byId(spellId);
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Spell written = SpellSource.spellOf(stack);
            if (written != null && spell != null && written.getId().equals(spell.getId())
                    && stack.is(MiscItemRegistry.TORN_SPELL_PAGE.get())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    // -- helpers ----------------------------------------------------------------------------------

    private static void openFromHand(GameTestHelper helper, ServerPlayer player, ItemStack stack) {
        stack.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        stack.getItem().finishUsingItem(stack, helper.getLevel(), player);
    }

    private static ItemStack book(Spell spell) {
        ItemStack book = new ItemStack(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get());
        SpellSource.write(book, spell);
        return book;
    }

    private static ItemStack page(Spell spell, int count) {
        ItemStack page = new ItemStack(MiscItemRegistry.TORN_SPELL_PAGE.get(), count);
        SpellSource.write(page, spell);
        return page;
    }

    private static PlayerSpellData spells(ServerPlayer player) {
        return WizardTestSupport.spellData(player);
    }
}
