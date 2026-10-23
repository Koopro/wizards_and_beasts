package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.brew.knowledge.BrewKnowledge;
import at.koopro.wizardsandbeasts.brew.knowledge.BrewRecipeBook;
import at.koopro.wizardsandbeasts.brew.knowledge.PotionRecipeItem;
import at.koopro.wizardsandbeasts.registry.CanonItemRegistry;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.MiscItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Recipe knowledge (Phase 5, slice 2): the basics are known to everyone, a textbook or a page teaches by being read
 * and is kept, the apothecary's lessons teach in order, and a recipe nobody taught you brews as an experiment.
 */
public final class RecipeKnowledgeTests {

    private static final String PEPPERUP = "wizards_and_beasts:pepperup_potion";
    private static final String SKELE_GRO = "wizards_and_beasts:skele_gro";
    private static final String SILVER = "wizards_and_beasts:silver_solution";
    private static final String POLYJUICE = "wizards_and_beasts:polyjuice_potion";

    private RecipeKnowledgeTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("recipes_a_textbook_teaches_and_is_kept",
                "brewing: the basics are known from the start; reading Moste Potente Potions teaches Polyjuice and "
                        + "the book stays in hand; a loose page teaches the recipe written on it",
                RecipeKnowledgeTests::textbookTeaches);
        tests.add("recipes_lessons_teach_in_order",
                "brewing: the apothecary's Potions lessons teach the next recipe on their list the player does not "
                        + "know, skipping one already learned",
                RecipeKnowledgeTests::lessonsTeachInOrder);
        tests.add("recipes_an_untaught_recipe_is_an_experiment",
                "brewing: Skele-Gro started by a brewer who never learned it is flagged an experiment; Pepperup, a "
                        + "basic, is not",
                RecipeKnowledgeTests::untaughtIsExperiment);
    }

    private static void textbookTeaches(GameTestHelper helper) {
        ServerPlayer player = player(helper, "RecipeReader");
        try {
            check(helper, BrewKnowledge.knows(player, PEPPERUP), () -> "the basic Pepperup is not known");
            check(helper, !BrewKnowledge.knows(player, POLYJUICE), () -> "Polyjuice known before any book was read");

            ItemStack book = new ItemStack(CanonItemRegistry.MOSTE_POTENTE_POTIONS.get());
            check(helper, PotionRecipeItem.recipesIn(book, true).contains(POLYJUICE),
                    () -> "the book's source does not list Polyjuice: " + PotionRecipeItem.recipesIn(book, true));
            ItemStack after = book.getItem().finishUsingItem(book, helper.getLevel(), player);
            check(helper, BrewKnowledge.knows(player, POLYJUICE), () -> "reading the book taught nothing");
            check(helper, after == book && book.getCount() == 1, () -> "the book was consumed: " + after);

            ItemStack page = new ItemStack(MiscItemRegistry.POTION_RECIPE_PAGE.get());
            page.set(ModDataComponents.BREW_RECIPES.get(), List.of(SILVER));
            page.getItem().finishUsingItem(page, helper.getLevel(), player);
            check(helper, BrewKnowledge.knows(player, SILVER), () -> "the page did not teach its recipe");
            check(helper, !BrewKnowledge.book(player).has(PEPPERUP), () -> "a basic was written into the book");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void lessonsTeachInOrder(GameTestHelper helper) {
        ServerPlayer player = player(helper, "RecipeLesson");
        try {
            check(helper, SKELE_GRO.equals(BrewKnowledge.nextLesson(player)),
                    () -> "the first lesson is not Skele-Gro: " + BrewKnowledge.nextLesson(player));
            BrewKnowledge.learn(player, List.of(SKELE_GRO));
            check(helper, SILVER.equals(BrewKnowledge.nextLesson(player)),
                    () -> "the second lesson is not the silver solution: " + BrewKnowledge.nextLesson(player));
            check(helper, BrewKnowledge.learn(player, List.of(SKELE_GRO)).isEmpty(),
                    () -> "a known recipe was learned again");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void untaughtIsExperiment(GameTestHelper helper) {
        ServerPlayer player = player(helper, "RecipeExperiment");
        try {
            player.setData(ModAttachments.BREW_RECIPE_BOOK.get(), BrewRecipeBook.EMPTY);
            CauldronBlockEntity skeleGro = cauldron(helper, new BlockPos(0, 1, 0), ModBlocks.BRASS_CAULDRON.get());
            add(skeleGro, ConsumableItemRegistry.DITTANY.get(), 2);
            add(skeleGro, Items.BONE_MEAL, 3);
            add(skeleGro, Items.GHAST_TEAR, 1);
            CauldronBlockEntity.StartResult started = skeleGro.startBrewing(CauldronTier.BRASS, player);
            check(helper, started == CauldronBlockEntity.StartResult.STARTED, () -> "setup: Skele-Gro " + started);
            check(helper, skeleGro.isExperiment(), () -> "an untaught Skele-Gro is not an experiment");

            CauldronBlockEntity pepperup = cauldron(helper, new BlockPos(3, 1, 0), ModBlocks.PEWTER_CAULDRON.get());
            add(pepperup, Items.BLAZE_POWDER, 1);
            add(pepperup, Items.SUGAR, 1);
            CauldronBlockEntity.StartResult basic = pepperup.startBrewing(CauldronTier.PEWTER, player);
            check(helper, basic == CauldronBlockEntity.StartResult.STARTED, () -> "setup: Pepperup " + basic);
            check(helper, !pepperup.isExperiment(), () -> "the basic Pepperup was brewed as an experiment");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // ── setup ───────────────────────────────────────────────────────────

    private static CauldronBlockEntity cauldron(GameTestHelper helper, BlockPos at, net.minecraft.world.level.block.Block block) {
        helper.setBlock(at.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(at, block);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CauldronBlockEntity pot)) {
            throw new IllegalStateException("no cauldron block entity at " + at);
        }
        pot.setFilled(true);
        return pot;
    }

    private static void add(CauldronBlockEntity pot, Item item, int count) {
        for (int i = 0; i < count; i++) {
            pot.addIngredient(new ItemStack(item));
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
