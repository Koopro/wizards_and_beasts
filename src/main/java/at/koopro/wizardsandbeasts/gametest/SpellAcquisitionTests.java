package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageAPI;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import at.koopro.wizardsandbeasts.spell.learning.SpellLearningService;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * How spells are acquired (Phase 7): the starter charm every wand hand knows.
 */
public final class SpellAcquisitionTests {

    private static final String LUMOS = "wizards_and_beasts:lumos";
    private static final String NOX = "wizards_and_beasts:nox";

    private SpellAcquisitionTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("spell_acquisition_starter_charm",
                "spells: a wizard knows Lumos and Nox without a book, kept out of the web ledger so a respec cannot "
                        + "take them; a goblin, who cannot use a wand, is taught nothing",
                SpellAcquisitionTests::starterCharm);
        tests.add("spell_acquisition_books_to_copy_and_first_lessons",
                "spells: blank spellbooks can be crafted and are sold at Ollivanders; First Lessons is earned by "
                        + "learning from a book, not by picking one up",
                SpellAcquisitionTests::blanksAndFirstLessons);
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

    private static void starterCharm(GameTestHelper helper) {
        ServerPlayer wizard = player(helper, "FirstLight");
        ServerPlayer goblin = player(helper, "NoWandHand");
        try {
            HeritageAPI.commit(wizard, Heritage.WIZARDKIND, HeritageVariant.HALF_BLOOD);
            HeritageAPI.commit(goblin, Heritage.GOBLIN, HeritageVariant.GOBLIN_COMMON);
            wizard.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(LUMOS);
            wizard.getData(ModAttachments.SPELL_DATA.get()).forgetSpell(NOX);

            var taught = SpellLearningService.teachStarter(wizard);
            PlayerSpellData spells = wizard.getData(ModAttachments.SPELL_DATA.get());
            check(helper, spells.knowsSpell(LUMOS) && spells.knowsSpell(NOX),
                    () -> "the wizard was not taught the starter charms: " + taught);
            check(helper, !wizard.getData(ModAttachments.SKILL_DATA.get()).getWebTaughtSpells().contains(LUMOS),
                    () -> "the starter charm went into the web ledger, where a respec would take it back");
            check(helper, SpellLearningService.teachStarter(wizard).isEmpty(), () -> "the starter was taught twice");

            check(helper, SpellLearningService.teachStarter(goblin).isEmpty()
                            && !goblin.getData(ModAttachments.SPELL_DATA.get()).knowsSpell(LUMOS),
                    () -> "a goblin, who cannot use a wand, was taught Lumos");
            helper.succeed();
        } finally {
            retire(helper, wizard);
            retire(helper, goblin);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
