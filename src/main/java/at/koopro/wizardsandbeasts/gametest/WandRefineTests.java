package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.wand.WandAppearance;
import at.koopro.wizardsandbeasts.wand.WandComponents;
import at.koopro.wizardsandbeasts.wand.bench.WandmakersBenchBlockEntity;
import at.koopro.wizardsandbeasts.wand.customization.WandSlot;
import at.koopro.wizardsandbeasts.wand.refine.WandRefineMenu;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Action;
import at.koopro.wizardsandbeasts.wand.refine.WandRefinement.Status;
import at.koopro.wizardsandbeasts.wand.stat.WandCore;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import at.koopro.wizardsandbeasts.wand.stat.WandLength;
import at.koopro.wizardsandbeasts.wand.stat.WandWood;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;

/**
 * The bench's second role (Phase 4): a finished wand is polished, re-fitted and re-handled at a furnished bench, the
 * material is spent, and wood, core and bond come out as they went in (2026-10-22).
 */
public final class WandRefineTests {

    /** Bench and enhancers inside the scenario's own column (x/z 0..4). */
    private static final BlockPos BENCH = new BlockPos(2, 1, 2);

    private WandRefineTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("wandmaking_refine_polish_refit_rehandle",
                "wandmaking: at a furnished bench a wand is polished (stronger with a cauldron), re-fitted a step "
                        + "(a lectern stands in for Wandlore) and given a new handle; material spent, bond untouched",
                WandRefineTests::refine);
    }

    private static void refine(GameTestHelper helper) {
        ServerPlayer maker = WizardTestSupport.placeMockPlayer(helper, "WandRefiner", GameType.SURVIVAL);
        try {
            WizardTestSupport.parkAtOrigin(helper, maker);
            helper.setBlock(BENCH, ModBlocks.WANDMAKERS_BENCH.get());
            // Two lecterns (2.0 each) for the re-fit tier and the grain, one pewter cauldron for a stronger polish.
            helper.setBlock(new BlockPos(0, 1, 4), ModBlocks.SPELL_TEACHER.get());
            helper.setBlock(new BlockPos(4, 1, 4), ModBlocks.SPELL_TEACHER.get());
            helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.PEWTER_CAULDRON.get());
            WandmakersBenchBlockEntity bench = (WandmakersBenchBlockEntity) helper.getLevel()
                    .getBlockEntity(helper.absolutePos(BENCH));
            check(helper, bench != null, () -> "setup: no bench");
            WandRefineMenu menu = new WandRefineMenu(0, maker.getInventory(), bench);

            ItemStack wand = WandItem.createWand(WandWood.HOLLY, WandCore.PHOENIX_FEATHER, WandLength.values()[0],
                    WandFlexibility.PLIANT);
            wand.set(WandComponents.WAND_INTEGRITY.get(), 0.5f);
            float bond = WandComponents.getAllegianceScore(wand);

            // Polish: honeycomb, +0.40 with the cauldron.
            ItemStack polished = run(helper, menu, maker, wand, new ItemStack(Items.HONEYCOMB), Action.POLISH);
            check(helper, Math.abs(WandComponents.getIntegrity(polished) - 0.9f) < 1e-4f,
                    () -> "polish gave integrity " + WandComponents.getIntegrity(polished));

            // Re-fit: a log of its own wood (spruce stands in for holly), one step stiffer.
            ItemStack refitted = run(helper, menu, maker, polished, new ItemStack(Items.SPRUCE_LOG), Action.STIFFER);
            check(helper, WandComponents.getFlexibility(refitted) == WandFlexibility.RIGID,
                    () -> "re-fit gave " + WandComponents.getFlexibility(refitted));

            // A log of another wood will not re-fit it.
            menu.slots.get(0).set(refitted.copy());
            menu.slots.get(1).set(new ItemStack(Items.BIRCH_LOG));
            menu.chooseAction(Action.SUPPLER.ordinal());
            check(helper, menu.status() == Status.WRONG_MATERIAL, () -> "another wood's log re-fitted: " + menu.status());
            menu.slots.get(0).set(ItemStack.EMPTY);
            menu.slots.get(1).set(ItemStack.EMPTY);

            // Re-handle: any log, the next handle; nothing else moves.
            Identifier before = WandAppearance.configuration(helper.getLevel().registryAccess(), refitted)
                    .getModule(WandSlot.HANDLE).orElse(null);
            ItemStack rehandled = run(helper, menu, maker, refitted, new ItemStack(Items.OAK_LOG), Action.HANDLE);
            Identifier after = WandAppearance.configuration(helper.getLevel().registryAccess(), rehandled)
                    .getModule(WandSlot.HANDLE).orElse(null);
            check(helper, after != null && !after.equals(before), () -> "the handle did not change: " + before + " -> " + after);
            check(helper, WandComponents.getWood(rehandled).equals(WandComponents.getWood(wand))
                            && WandComponents.getCore(rehandled).equals(WandComponents.getCore(wand))
                            && WandComponents.getAllegianceScore(rehandled) == bond,
                    () -> "refining changed the wand's wood, core or bond");
            helper.succeed();
        } finally {
            WizardTestSupport.retire(helper, maker);
        }
    }

    /** Puts the wand and material in, picks the action, takes the output; returns what came out. */
    private static ItemStack run(GameTestHelper helper, WandRefineMenu menu, ServerPlayer maker, ItemStack wand,
                                 ItemStack material, Action action) {
        menu.slots.get(0).set(wand.copy());
        menu.slots.get(1).set(material);
        menu.chooseAction(action.ordinal());
        check(helper, menu.status() == Status.READY, () -> action + " is not ready: " + menu.status());
        ItemStack out = menu.slots.get(2).getItem().copy();
        check(helper, !out.isEmpty(), () -> action + " put nothing in the output");
        menu.slots.get(2).onTake(maker, out);
        check(helper, menu.slots.get(0).getItem().isEmpty() && menu.slots.get(1).getItem().isEmpty(),
                () -> action + " did not spend the wand put in and its material");
        return out;
    }
}
