package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import at.koopro.wizardsandbeasts.spell.core.Spell;
import at.koopro.wizardsandbeasts.spell.core.Spells;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The loop that replaced the spell teacher, driven end to end.
 *
 * <p>The vendor was one screen and two packets, and everything that could go wrong with it was
 * visible in the screen. What replaced it is spread across an item, a block, a data component and a
 * loot modifier, and the failure modes are all silent: a book that reads as blank because the
 * component did not survive a copy, a scribed book that never reaches the inventory, a gate that
 * stops refusing because the source path forgot to ask.
 *
 * <p>So these drive the real methods a player's click reaches — {@code SpellSourceItem.use} and
 * {@code finishUsingItem}, and the block's {@code useItemOn} — rather than the service underneath
 * them, which the unit tests already cover on its own.
 */
public final class SpellLearningTests {

    /** Two spells with no requirements, so nothing here depends on a progression gate being open. */
    private static final String TAUGHT_SPELL = "lumos";
    private static final String SCRIBED_SPELL = "nox";
    /** A node that carries a `learn_spell` effect, and the spell it carries. */
    private static final String SKILL_NODE = "frigora_unlock";
    private static final String SKILL_TAUGHT_SPELL = "frigora";
    private static final String COMMAND_TAUGHT_SPELL = "alohomora";

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos LECTERN = new BlockPos(1, 1, 1);
    private static final Vec3 PLAYER_STAND = new Vec3(4.5, 1.0, 4.5);

    private SpellLearningTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_source_teaches_the_spell_written_in_it",
                "spell source: finishing a read learns the spell and leaves the book intact",
                SpellLearningTests::sourceTeachesItsSpell);
        tests.add("spell_source_refuses_a_spell_already_known",
                "spell source: the eligibility gates still refuse through the reading path",
                SpellLearningTests::sourceRefusesWhatIsAlreadyKnown);
        tests.add("spell_source_blank_teaches_nothing",
                "spell source: a blank book opens no read and teaches nothing",
                SpellLearningTests::blankSourceTeachesNothing);
        tests.add("spell_source_respects_the_required_skill_gate",
                "spell source: a skill-gated spell refuses until the node is allocated",
                SpellLearningTests::skillGateStillRefuses);
        tests.add("spell_source_survives_an_unresolvable_spell_id",
                "spell source: a page naming a spell that does not exist refuses instead of throwing",
                SpellLearningTests::badIdIsRefusedNotThrown);
        tests.add("spell_source_page_is_spent_only_on_success",
                "torn page: destroyed by a successful read, kept when a gate refuses it",
                SpellLearningTests::pageIsSpentOnlyOnSuccess);
        tests.add("skill_node_still_teaches_its_spell",
                "skill web: a learn_spell keystone still grants, with the vendor's service gone",
                SpellLearningTests::skillNodeStillTeaches);
        tests.add("spell_learn_command_still_works",
                "command: /wandb magic spell learn still teaches, and stores a canonical id",
                SpellLearningTests::learnCommandStillWorks);
        tests.add("spell_scriptorium_scribes_the_active_spell",
                "study lectern: a blank book plus ink becomes a book of the active spell",
                SpellLearningTests::lecternScribesTheActiveSpell);
    }

    // -- scenarios ---------------------------------------------------------------------------------

    private static void sourceTeachesItsSpell(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Reader");
        try {
            Spell spell = requireSpell(helper, TAUGHT_SPELL);
            PlayerSpellData data = WizardTestSupport.spellData(player);
            WizardTestSupport.check(helper, !data.knowsSpell(spell.getId()),
                    () -> "a fresh test player already knew " + spell.getId());

            ItemStack book = writtenBook(spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);

            InteractionResult opened = book.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            WizardTestSupport.check(helper, opened == InteractionResult.CONSUME,
                    () -> "reading an eligible book did not open a read channel: " + opened);

            book.getItem().finishUsingItem(book, helper.getLevel(), player);

            WizardTestSupport.check(helper, data.knowsSpell(spell.getId()),
                    () -> "finishing the read did not teach " + spell.getId());
            // Not consumed: a textbook someone has read is still a textbook, and a server's second
            // wizard has to be able to borrow it.
            WizardTestSupport.check(helper, !book.isEmpty() && SpellSource.spellOf(book) != null,
                    () -> "the book was spent or wiped by being read");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The gate that matters most, because it is the one the vendor used to enforce by simply not
     * listing the spell. A source has no catalogue to omit anything from, so the refusal has to come
     * from the eligibility layer on every read.
     */
    private static void sourceRefusesWhatIsAlreadyKnown(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Rereader");
        try {
            Spell spell = requireSpell(helper, TAUGHT_SPELL);
            WizardTestSupport.spellData(player).learnSpell(spell.getId());

            ItemStack book = writtenBook(spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, book);

            InteractionResult result = book.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            WizardTestSupport.check(helper, result == InteractionResult.FAIL,
                    () -> "re-reading a known spell was allowed to open a read channel: " + result);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void blankSourceTeachesNothing(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "BlankReader");
        try {
            PlayerSpellData data = WizardTestSupport.spellData(player);
            int knownBefore = data.getKnownSpells().size();

            ItemStack blank = new ItemStack(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get());
            player.setItemInHand(InteractionHand.MAIN_HAND, blank);

            InteractionResult result = blank.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            // Driven anyway, because a blank that somehow reached the finish would be the silent bug.
            blank.getItem().finishUsingItem(blank, helper.getLevel(), player);

            WizardTestSupport.check(helper, result == InteractionResult.FAIL,
                    () -> "a blank book opened a read channel: " + result);
            WizardTestSupport.check(helper, data.getKnownSpells().size() == knownBefore,
                    () -> "a blank book taught something");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The skill web's own learning path, which never went through the vendor and must not have been
     * caught by its removal.
     *
     * <p>`SkillSystemAPI.teachSpell` writes `PlayerSpellData` and records the grant in the web-taught
     * ledger — the ledger being what a respec later revokes, and the reason a spell learned some other
     * way is never confiscated by one. Both halves are asserted, because a grant that skipped the
     * ledger would look correct here and quietly survive a respec forever.
     */
    private static void skillNodeStillTeaches(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Scholar");
        try {
            Spell spell = requireSpell(helper, SKILL_TAUGHT_SPELL);
            PlayerSpellData data = WizardTestSupport.spellData(player);
            WizardTestSupport.check(helper, !data.knowsSpell(spell.getId()),
                    () -> "a fresh test player already knew " + spell.getId());

            at.koopro.wizardsandbeasts.skill.SkillSystemAPI.forceUnlock(player, SKILL_NODE);

            WizardTestSupport.check(helper, data.knowsSpell(spell.getId()),
                    () -> "allocating " + SKILL_NODE + " did not teach " + spell.getId());
            WizardTestSupport.check(helper,
                    at.koopro.wizardsandbeasts.skill.SkillSystemAPI.getSkillData(player)
                            .getWebTaughtSpells().contains(spell.getId()),
                    () -> "the grant was not recorded in the web-taught ledger, so a respec could not revoke it");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * `/wandb magic spell learn` — run through the real dispatcher, as the player, at operator level.
     *
     * <p>It deliberately writes `PlayerSpellData` directly rather than going through
     * `SpellLearningService`: an operator granting a spell is overriding the gates, and a debug
     * command that could be refused by the rules it exists to step around is useless for setting up
     * the exact state a test needs. So this asserts it still teaches, and that the command tree still
     * has the node at all — the vendor's removal touched the same package.
     */
    private static void learnCommandStillWorks(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Operator");
        try {
            Spell spell = requireSpell(helper, COMMAND_TAUGHT_SPELL);
            PlayerSpellData data = WizardTestSupport.spellData(player);
            WizardTestSupport.check(helper, !data.knowsSpell(spell.getId()),
                    () -> "a fresh test player already knew " + spell.getId());

            var server = helper.getLevel().getServer();
            WizardTestSupport.check(helper,
                    server.getCommands().getDispatcher().getRoot().getChild("wandb") != null,
                    () -> "/wandb is not registered at all");

            // The server's own source rather than the player's: it is already at full permission, and
            // 1.21.11 replaced withPermission(int) with a PermissionSet there is no literal for here.
            // withEntity is what the command actually reads — it calls getPlayerOrException.
            // The bare path, which is what the command's own completion offers and what its
            // StringArgumentType.word() argument can actually parse. Passing spell.getId() here is
            // what caught the suggestions offering namespaced ids the parser rejects.
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withEntity(player).withSuppressedOutput(),
                    "wandb magic spell learn " + COMMAND_TAUGHT_SPELL);

            WizardTestSupport.check(helper, data.knowsSpell(spell.getId()),
                    () -> "/wandb magic spell learn did not teach " + spell.getId()
                            + " — known set holds " + data.getKnownSpells());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * The one gate that cannot be reached without a player, and therefore cannot be unit-tested.
     *
     * <p>{@code requiredSkillId} is read off {@code PlayerSkillData}, so the eligibility layer skips
     * the branch entirely when it has no player — which is exactly what every unit test gives it. A
     * spell source always has one.
     */
    private static void skillGateStillRefuses(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Unqualified");
        try {
            Spell gated = new SkillGatedSpell();
            gated.init();

            var result = at.koopro.wizardsandbeasts.spell.learning.SpellLearningService
                    .validateLearnAttempt(player, gated);

            WizardTestSupport.check(helper, !result.success(),
                    () -> "a spell requiring an unallocated skill node was learnable");
            WizardTestSupport.check(helper, result.message().contains(SkillGatedSpell.REQUIRED_NODE),
                    () -> "the refusal did not name the missing node: " + result.message());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * A source naming a spell that does not resolve.
     *
     * <p>Reachable from a datapack typo, an outdated save, or a spell removed between versions — and
     * the id lives in a data component, so nothing validates it between being written and being read.
     * Both halves of the read are driven: `use` must refuse without throwing, and `finishUsingItem`
     * must survive being called anyway, which is what a client that already began the animation will
     * cause.
     */
    private static void badIdIsRefusedNotThrown(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "BadIdReader");
        try {
            PlayerSpellData data = WizardTestSupport.spellData(player);
            int knownBefore = data.getKnownSpells().size();

            ItemStack page = new ItemStack(MiscItemRegistry.TORN_SPELL_PAGE.get());
            page.set(ModDataComponents.SPELL_SOURCE.get(), "wizards_and_beasts:definitely_not_a_spell");
            player.setItemInHand(InteractionHand.MAIN_HAND, page);

            // Not blank — an id is present — so this is the branch that has to resolve it and fail.
            WizardTestSupport.check(helper, !SpellSource.isBlank(page),
                    () -> "the fixture wrote no id, so this scenario is testing nothing");
            WizardTestSupport.check(helper, SpellSource.spellOf(page) == null,
                    () -> "the fixture's nonsense id resolved to a real spell");

            InteractionResult result = page.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            page.getItem().finishUsingItem(page, helper.getLevel(), player);

            WizardTestSupport.check(helper, result == InteractionResult.FAIL,
                    () -> "an unresolvable id opened a read channel: " + result);
            WizardTestSupport.check(helper, data.getKnownSpells().size() == knownBefore,
                    () -> "an unresolvable id taught something");
            WizardTestSupport.check(helper, !page.isEmpty(),
                    () -> "a refused page was destroyed anyway");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    /**
     * A loose source is spent by studying it, and only by studying it.
     *
     * <p>The failure worth pinning is the second half. Destroying a page on a <em>refused</em> read
     * would mean an unmet requirement costs the player the only copy they had of the spell they
     * cannot learn yet — an item that punishes you for not already having what it teaches.
     */
    private static void pageIsSpentOnlyOnSuccess(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "PageReader");
        try {
            Spell spell = requireSpell(helper, TAUGHT_SPELL);
            player.getAbilities().instabuild = false; // creative deliberately spends nothing

            ItemStack page = new ItemStack(MiscItemRegistry.TORN_SPELL_PAGE.get(), 2);
            SpellSource.write(page, spell);
            player.setItemInHand(InteractionHand.MAIN_HAND, page);

            page.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            page.getItem().finishUsingItem(page, helper.getLevel(), player);

            WizardTestSupport.check(helper, WizardTestSupport.spellData(player).knowsSpell(spell.getId()),
                    () -> "reading the page did not teach " + spell.getId());
            WizardTestSupport.check(helper, page.getCount() == 1,
                    () -> "a successful read did not spend exactly one page (" + page.getCount() + " left)");

            // Second read: the spell is known now, so the gate refuses — and the page must survive.
            InteractionResult refused = page.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
            page.getItem().finishUsingItem(page, helper.getLevel(), player);

            WizardTestSupport.check(helper, refused == InteractionResult.FAIL,
                    () -> "re-reading a known spell was allowed: " + refused);
            WizardTestSupport.check(helper, page.getCount() == 1,
                    () -> "a refused read spent the page anyway");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void lecternScribesTheActiveSpell(GameTestHelper helper) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, "Scribe");
        try {
            helper.setBlock(FLOOR, Blocks.STONE);
            helper.setBlock(LECTERN, ModBlocks.SPELL_TEACHER.get());
            player.snapTo(helper.absoluteVec(PLAYER_STAND).x, helper.absoluteVec(PLAYER_STAND).y,
                    helper.absoluteVec(PLAYER_STAND).z, 0f, 0f);

            Spell spell = requireSpell(helper, SCRIBED_SPELL);
            WizardTestSupport.learnAndSelect(helper, player, SCRIBED_SPELL, null);

            // The mock player is creative, and creative scribing deliberately spends nothing. Survival
            // is the case worth pinning: it is the one where the ink cost exists at all.
            player.getAbilities().instabuild = false;

            // The book goes in hand first. Inventory.add can land a stack in the selected hotbar slot,
            // and setItemInHand writes that same slot — so adding the ink first put it exactly where the
            // book was about to overwrite it, and the scribe arrived at the desk with nothing to write with.
            ItemStack blank = new ItemStack(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get(), 1);
            player.setItemInHand(InteractionHand.MAIN_HAND, blank);
            player.getInventory().add(new ItemStack(MiscItemRegistry.INK_BOTTLE.get(), 2));

            BlockPos absolute = helper.absolutePos(LECTERN);
            InteractionResult result = helper.getBlockState(LECTERN).useItemOn(
                    blank, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));

            WizardTestSupport.check(helper, result == InteractionResult.SUCCESS,
                    () -> "scribing at the lectern was refused: " + result);
            WizardTestSupport.check(helper, holdsBookOf(player, spell),
                    () -> "no book of " + spell.getId() + " reached the scribe's inventory");
            WizardTestSupport.check(helper, countOf(player, MiscItemRegistry.INK_BOTTLE.get().getDefaultInstance()) == 1,
                    () -> "scribing did not spend exactly one ink bottle");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // -- helpers -----------------------------------------------------------------------------------

    private static Spell requireSpell(GameTestHelper helper, String id) {
        Spell spell = Spells.byId(id);
        if (spell == null) {
            helper.fail("test spell '" + id + "' is not registered; this scenario cannot run without it");
        }
        return spell;
    }

    private static ItemStack writtenBook(Spell spell) {
        ItemStack book = new ItemStack(CanonItemRegistry.STANDARD_BOOK_OF_SPELLS.get());
        SpellSource.write(book, spell);
        return book;
    }

    private static boolean holdsBookOf(ServerPlayer player, Spell spell) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            Spell written = SpellSource.spellOf(stack);
            if (written != null && written.getId().equals(spell.getId())) {
                return true;
            }
        }
        return false;
    }

    private static int countOf(ServerPlayer player, ItemStack sample) {
        int total = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(sample.getItem())) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** A spell gated on a skill node no test player has. Never registered — the gates take a `Spell`. */
    private static final class SkillGatedSpell extends at.koopro.wizardsandbeasts.spell.core.Spell {
        static final String REQUIRED_NODE = "gametest_node_nobody_has";

        private SkillGatedSpell() {
            super("gametest_skill_gated", "Game Test Skill Gated",
                    at.koopro.wizardsandbeasts.spell.core.SpellCategory.UTILITY, 20, 0f, 0xFFFFFF);
        }

        @Override
        public String getRequiredSkillId() {
            return REQUIRED_NODE;
        }

        @Override
        protected at.koopro.wizardsandbeasts.spell.core.SpellProperties buildProperties() {
            return null;
        }

        @Override
        protected at.koopro.wizardsandbeasts.spell.core.SpellRequirement buildRequirement() {
            return at.koopro.wizardsandbeasts.spell.core.SpellRequirement.none();
        }
    }
}
