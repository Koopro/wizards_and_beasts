package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.MandrakeCropBlock;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.item.brew.BrewItem;
import at.koopro.wizardsandbeasts.polyjuice.PolyjuiceSample;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.spell.petrify.PetrifyServerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * Where brewing's rarer things come from (Phase 5, slice 4): Devil's Snare comes away in a bare hand, a wild
 * Mandrake roots in plain ground, a Polyjuice bottle takes a hair from the person it is pressed to, and a Mandrake
 * Restoration Draught is poured over someone petrified.
 */
public final class BrewSourceTests {

    private BrewSourceTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("brew_source_devils_snare_by_hand",
                "brewing: Devil's Snare broken by a bare hand drops itself",
                BrewSourceTests::snareByHand);
        tests.add("brew_source_wild_mandrake_roots_in_grass",
                "brewing: a wild-age Mandrake survives on grass; a seedling there does not",
                BrewSourceTests::wildMandrake);
        tests.add("brew_source_polyjuice_takes_a_hair",
                "brewing: a Polyjuice bottle pressed to another player takes their hair, splitting one off a stack; "
                        + "a bottle with a hair is not refilled",
                BrewSourceTests::polyjuiceTakesAHair);
        tests.add("brew_source_restoration_poured_over_the_petrified",
                "brewing: a Mandrake Restoration Draught used on a petrified player cures them and leaves a glass "
                        + "bottle; on a player who is not petrified it is kept",
                BrewSourceTests::restorationPouredOver);
    }

    private static void snareByHand(GameTestHelper helper) {
        ServerPlayer player = player(helper, "SnarePuller");
        try {
            BlockPos at = new BlockPos(1, 1, 1);
            helper.setBlock(at, ModBlocks.DEVILS_SNARE.get());
            BlockState state = helper.getBlockState(at);
            check(helper, !state.requiresCorrectToolForDrops(), () -> "Devil's Snare still wants a tool for drops");
            List<ItemStack> drops = Block.getDrops(state, helper.getLevel(), helper.absolutePos(at), null, player,
                    ItemStack.EMPTY);
            check(helper, drops.stream().anyMatch(s -> s.is(ModBlocks.DEVILS_SNARE.get().asItem())),
                    () -> "a bare hand got " + drops);
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, player);
        }
    }

    private static void wildMandrake(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 0, 1), Blocks.GRASS_BLOCK);
        helper.setBlock(new BlockPos(3, 0, 1), Blocks.GRASS_BLOCK);
        MandrakeCropBlock crop = ModBlocks.MANDRAKE_CROP.get();
        BlockState wild = crop.getStateForAge(MandrakeCropBlock.WILD_AGE);
        BlockState seedling = crop.getStateForAge(0);
        check(helper, wild.canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1))),
                () -> "a wild Mandrake cannot stand on grass");
        check(helper, !seedling.canSurvive(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1))),
                () -> "a seedling took root in untilled grass");
        helper.succeed();
    }

    private static void polyjuiceTakesAHair(GameTestHelper helper) {
        ServerPlayer user = player(helper, "HairTaker");
        ServerPlayer target = player(helper, "HairGiver");
        ServerPlayer other = player(helper, "HairOther");
        try {
            user.getInventory().clearContent();
            ItemStack bottles = BrewItem.of(Brews.byId(BrewItem.POLYJUICE));
            bottles.setCount(2);
            user.setItemInHand(InteractionHand.MAIN_HAND, bottles);
            bottles.getItem().interactLivingEntity(bottles, user, target, InteractionHand.MAIN_HAND);
            check(helper, bottles.getCount() == 1 && !PolyjuiceSample.read(bottles).isPresent(),
                    () -> "the stack in hand was not split cleanly: " + bottles);
            ItemStack withHair = user.getInventory().getNonEquipmentItems().stream()
                    .filter(s -> PolyjuiceSample.read(s).isPresent()).findFirst().orElse(ItemStack.EMPTY);
            check(helper, PolyjuiceSample.read(withHair).id().filter(target.getUUID()::equals).isPresent(),
                    () -> "no bottle carries the target's hair");

            withHair.getItem().interactLivingEntity(withHair, user, other, InteractionHand.MAIN_HAND);
            check(helper, PolyjuiceSample.read(withHair).id().filter(target.getUUID()::equals).isPresent(),
                    () -> "a bottle with a hair took a second one");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, user);
            WizardTestSupport.retire(helper, target);
            WizardTestSupport.retire(helper, other);
        }
    }

    private static void restorationPouredOver(GameTestHelper helper) {
        ServerPlayer healer = player(helper, "StoneHealer");
        ServerPlayer stone = player(helper, "StoneVictim");
        try {
            healer.getInventory().clearContent();
            ItemStack draught = BrewItem.of(Brews.byId(BrewItem.MANDRAKE_RESTORATION));
            healer.setItemInHand(InteractionHand.MAIN_HAND, draught);
            draught.getItem().interactLivingEntity(draught, healer, stone, InteractionHand.MAIN_HAND);
            check(helper, draught.getCount() == 1, () -> "a draught was spent on someone not petrified");

            PetrifyServerLogic.beginPetrify(helper.getLevel(), stone);
            check(helper, PetrifyServerLogic.isPetrified(stone), () -> "setup: the victim did not turn to stone");
            draught.getItem().interactLivingEntity(draught, healer, stone, InteractionHand.MAIN_HAND);
            check(helper, !PetrifyServerLogic.isPetrified(stone), () -> "the draught did not cure the victim");
            check(helper, draught.isEmpty(), () -> "the draught was not spent");
            check(helper, healer.getInventory().getNonEquipmentItems().stream().anyMatch(s -> s.is(Items.GLASS_BOTTLE)),
                    () -> "no glass bottle came back");
            helper.succeed();
        } finally {
            PetrifyServerLogic.cure(helper.getLevel(), stone);
            WizardTestSupport.retire(helper, healer);
            WizardTestSupport.retire(helper, stone);
        }
    }

    private static ServerPlayer player(GameTestHelper helper, String name) {
        ServerPlayer player = WizardTestSupport.placeMockPlayer(helper, name, GameType.SURVIVAL);
        WizardTestSupport.parkAtOrigin(helper, player);
        return player;
    }
}
