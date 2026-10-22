package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The brewing loop's mechanics fixes (Phase 5, slice 1): a pot no longer swallows a sword; an Occamy eggshell refines
 * a silver brew on a plain click and is kept when it cannot; leftovers come back out; brew points follow the recipe's
 * tier; the bottle comes back after drinking.
 */
public final class BrewingFixTests {

    private BrewingFixTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("brewing_pot_refuses_a_sword",
                "brewing: a sword clicked into a cauldron is refused, not consumed",
                BrewingFixTests::refusesASword);
        tests.add("brewing_eggshell_refines_on_a_plain_click",
                "brewing: an Occamy eggshell clicked into a brewing silver solution refines it and is spent; into "
                        + "another brew it is refused and kept",
                BrewingFixTests::eggshellRefines);
        // Alone: its brewer stays online for the whole brew, and a player standing in a neighbouring column is
        // prey to the ecology scenarios' creatures (an acromantula went hunting it).
        tests.addAlone("brewing_leftovers_return_and_points_follow_the_recipe",
                "brewing: an extra ingredient comes back out when the pot is emptied; a Pepperup in a copper pot pays "
                        + "first-year points",
                BrewingFixTests::leftoversAndPoints);
        tests.add("brewing_drinking_returns_the_bottle",
                "brewing: drinking a brew gives the glass bottle back",
                BrewingFixTests::drinkingReturnsTheBottle);
    }

    private static void refusesASword(GameTestHelper helper) {
        ServerPlayer player = player(helper, "PotSword");
        try {
            CauldronBlockEntity pot = cauldron(helper, new BlockPos(1, 1, 1), ModBlocks.PEWTER_CAULDRON.get());
            pot.setFilled(true);
            ItemStack sword = new ItemStack(Items.IRON_SWORD);
            click(helper, player, new BlockPos(1, 1, 1), sword);
            check(helper, sword.getCount() == 1 && pot.usedSlots() == 0, () -> "the sword went in: " + sword + ", slots " + pot.usedSlots());
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void eggshellRefines(GameTestHelper helper) {
        ServerPlayer player = player(helper, "PotSilver");
        try {
            CauldronBlockEntity silver = cauldron(helper, new BlockPos(0, 1, 0), ModBlocks.BRASS_CAULDRON.get());
            silver.setFilled(true);
            silver.addIngredient(new ItemStack(Items.IRON_INGOT));
            silver.addIngredient(new ItemStack(Items.AMETHYST_SHARD));
            silver.addIngredient(new ItemStack(Items.AMETHYST_SHARD));
            silver.addIngredient(new ItemStack(Items.GLOW_INK_SAC));
            check(helper, silver.startBrewing(CauldronTier.BRASS, player.getUUID()) == CauldronBlockEntity.StartResult.STARTED,
                    () -> "setup: silver solution did not start");
            ItemStack shells = new ItemStack(ConsumableItemRegistry.OCCAMY_EGGSHELL.get(), 2);
            click(helper, player, new BlockPos(0, 1, 0), shells);
            check(helper, shells.getCount() == 1, () -> "refining did not spend exactly one shell: " + shells.getCount());
            check(helper, "wizards_and_beasts:pure_silver_solution".equals(silver.brewId()),
                    () -> "the batch was not refined: " + silver.brewId());

            CauldronBlockEntity pepperup = cauldron(helper, new BlockPos(3, 1, 0), ModBlocks.PEWTER_CAULDRON.get());
            pepperup.setFilled(true);
            pepperup.addIngredient(new ItemStack(Items.BLAZE_POWDER));
            pepperup.addIngredient(new ItemStack(Items.SUGAR));
            check(helper, pepperup.startBrewing(CauldronTier.PEWTER, player.getUUID()) == CauldronBlockEntity.StartResult.STARTED,
                    () -> "setup: pepperup did not start");
            click(helper, player, new BlockPos(3, 1, 0), shells);
            check(helper, shells.getCount() == 1 && pepperup.accumulatedPenalties() == 0f,
                    () -> "a shell on a non-silver brew was spent or counted as contamination");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void leftoversAndPoints(GameTestHelper helper) {
        ServerPlayer player = player(helper, "PotLeftover");
        BlockPos at = new BlockPos(1, 1, 3);
        CauldronBlockEntity pot = cauldron(helper, at, ModBlocks.WIZARDING_COPPER_CAULDRON.get());
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.addIngredient(new ItemStack(Items.STICK));
        int before = player.getData(ModAttachments.SKILL_DATA.get()).getPotionBrewPoints();
        check(helper, pot.startBrewing(CauldronTier.COPPER, player.getUUID()) == CauldronBlockEntity.StartResult.STARTED,
                () -> "setup: pepperup did not start in copper");
        helper.startSequence()
                .thenWaitUntil(() -> check(helper, pot.phase() == CauldronPhase.DONE, () -> "still " + pot.phase()))
                .thenExecute(() -> {
                    check(helper, pot.collect() != null, () -> "nothing to collect");
                    int gained = player.getData(ModAttachments.SKILL_DATA.get()).getPotionBrewPoints() - before;
                    check(helper, gained == CauldronTier.PEWTER.brewPoints(),
                            () -> "a Pepperup in copper paid " + gained + " points, not the recipe tier's");
                    boolean stickBack = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            new AABB(helper.absolutePos(at)).inflate(2), e -> e.getItem().is(Items.STICK)).isEmpty();
                    check(helper, stickBack, () -> "the stick the recipe did not use vanished");
                })
                .thenExecute(() -> WizardTestSupport.retire(helper, player))
                .thenSucceed();
    }

    private static void drinkingReturnsTheBottle(GameTestHelper helper) {
        ServerPlayer player = player(helper, "PotDrinker");
        try {
            player.getInventory().clearContent();
            ItemStack brew = BrewItem.of(Brews.byId("wizards_and_beasts:pepperup_potion"));
            player.setItemInHand(InteractionHand.MAIN_HAND, brew);
            brew.getItem().finishUsingItem(brew, helper.getLevel(), player);
            boolean bottle = player.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.is(Items.GLASS_BOTTLE));
            check(helper, bottle, () -> "no glass bottle came back");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    // ── setup ───────────────────────────────────────────────────────────

    private static CauldronBlockEntity cauldron(GameTestHelper helper, BlockPos at, Block block) {
        helper.setBlock(at.below(), Blocks.MAGMA_BLOCK);
        helper.setBlock(at, block);
        if (!(helper.getLevel().getBlockEntity(helper.absolutePos(at)) instanceof CauldronBlockEntity pot)) {
            throw new IllegalStateException("no cauldron block entity at " + at);
        }
        return pot;
    }

    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos at, ItemStack stack) {
        BlockPos abs = helper.absolutePos(at);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
        helper.getLevel().getBlockState(abs).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
