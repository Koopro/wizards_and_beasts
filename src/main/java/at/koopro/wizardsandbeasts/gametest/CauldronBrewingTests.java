package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.block.brew.CauldronBlockEntity;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import at.koopro.wizardsandbeasts.skill.data.PlayerSkillData;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;

import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.check;
import static at.koopro.wizardsandbeasts.gametest.WizardTestSupport.retire;

/**
 * A pot of Pepperup, brewed start to finish in a live server.
 *
 * <p>This is the first game test of a block entity in this mod, and it covers a seam nothing else does:
 * {@code CauldronBlockEntity.serverTick} only runs when a real block is placed in a real level with a real
 * fire under it, and everything the brew depends on — the heat check, the clock, the completion transition
 * — happens inside it. A unit test can assert what a recipe matches; it cannot assert that a cauldron on a
 * magma block eventually holds a potion.
 *
 * <p>It also closes the loop on the one live O.W.L. input. {@code awardBrewPoints} is what feeds the
 * Potions grade, it runs on collection rather than on completion, and it pays the <em>brewer</em> rather
 * than whoever takes the bottle. That is asserted here rather than assumed.
 *
 * <p><b>Why Pepperup.</b> Of the twelve brewing recipes it is the only one that is both short and certain:
 * 160 heat ticks, {@code failureChance} 0, no catalyst window, two vanilla ingredients, and the pewter tier
 * every player starts with. Recipes with a failure chance would make this test a coin flip, and
 * {@code rollFailure} is rolled once at completion — so a flaky pass would look exactly like a real one.
 */
public final class CauldronBrewingTests {

    /** {@code pepperup_potion}: pewter, 160 ticks, cannot fail. */
    private static final int HEAT_TICKS = 160;
    private static final String EXPECTED_BREW = "wizards_and_beasts:pepperup_potion";

    /** Magma under the pot at the origin, cauldron on top of it. */
    private static final BlockPos HEAT = BlockPos.ZERO;
    private static final BlockPos CAULDRON = new BlockPos(0, 1, 0);

    private CauldronBrewingTests() {}

    static void contribute(WizardTestSupport.Registrar tests) {
        tests.add("cauldron_brews_and_pays_the_brewer", "cauldron: brew a Pepperup and collect it",
                CauldronBrewingTests::brewsAndPaysTheBrewer);
        tests.add("cauldron_without_heat_spoils", "cauldron: a pot off the boil spoils",
                CauldronBrewingTests::withoutHeatSpoils);
    }

    // ── the happy path, end to end ──────────────────────────────────────────────────────────────

    private static void brewsAndPaysTheBrewer(GameTestHelper helper) {
        ServerPlayer brewer = WizardTestSupport.placeMockPlayer(helper, "wandb-test-brewer");
        WizardTestSupport.parkAtOrigin(helper, brewer);
        PlayerSkillData skills = brewer.getData(ModAttachments.SKILL_DATA.get());
        int pointsBefore = skills.getPotionBrewPoints();

        CauldronBlockEntity pot = placeHeatedCauldron(helper);

        check(helper, pot.phase() == CauldronPhase.IDLE,
                () -> "a freshly placed cauldron should be idle, was " + pot.phase());

        // One ingredient at a time, the way the block takes them from a player's hand.
        check(helper, pot.addIngredient(new ItemStack(Items.BLAZE_POWDER)),
                () -> "the cauldron refused blaze powder");
        check(helper, pot.addIngredient(new ItemStack(Items.SUGAR)),
                () -> "the cauldron refused sugar");
        pot.setFilled(true);

        CauldronBlockEntity.StartResult start = pot.startBrewing(CauldronTier.PEWTER, brewer);
        check(helper, start == CauldronBlockEntity.StartResult.STARTED,
                () -> "startBrewing refused the Pepperup ingredients in a pewter pot: " + start);
        check(helper, pot.phase() == CauldronPhase.BREWING,
                () -> "the cauldron did not enter BREWING after a successful start, phase is " + pot.phase());

        helper.startSequence()
                // Mid-brew: proves serverTick is actually running the clock rather than the phase having
                // been set once and left. Also proves the heat check is passing on a magma block.
                .thenExecuteAfter(40, () -> {
                    check(helper, pot.phase() == CauldronPhase.BREWING,
                            () -> "the pot left BREWING 40 ticks in, phase is " + pot.phase()
                                    + " (spoiled means the magma block did not read as a heat source)");
                    check(helper, pot.remainingTicks() < HEAT_TICKS,
                            () -> "the brew clock did not advance in 40 ticks: remaining is "
                                    + pot.remainingTicks() + " of " + HEAT_TICKS);
                })
                .thenExecuteAfter(HEAT_TICKS - 40 + 5, () -> {
                    check(helper, pot.phase() == CauldronPhase.DONE,
                            () -> "the brew was not finished " + (HEAT_TICKS + 5) + " ticks in: phase is "
                                    + pot.phase() + ", remaining " + pot.remainingTicks());
                    check(helper, EXPECTED_BREW.equals(pot.brewId()),
                            () -> "the pot finished as '" + pot.brewId() + "', expected " + EXPECTED_BREW);
                    check(helper, skills.getPotionBrewPoints() == pointsBefore,
                            () -> "brew points were paid at completion; they are owed on collection, so a "
                                    + "brew nobody bottles should still pay nothing. Points went from "
                                    + pointsBefore + " to " + skills.getPotionBrewPoints());

                    Brew collected = pot.collect();
                    check(helper, collected != null,
                            () -> "collect() returned nothing from a finished pot");
                    check(helper, pot.phase() == CauldronPhase.IDLE,
                            () -> "the cauldron did not reset to idle after collection, phase is " + pot.phase());
                    check(helper, !pot.isFilled(),
                            () -> "the cauldron kept its water after collection, so one bucket would serve "
                                    + "every brew it ever makes");

                    // PEWTER.brewPoints() is 1, and HogwartsComfort.scaleStudy leaves an award untouched
                    // for a brewer with no Comfort effect — so this is an exact number, not a range.
                    int expected = CauldronTier.PEWTER.brewPoints();
                    check(helper, skills.getPotionBrewPoints() == pointsBefore + expected,
                            () -> "collecting paid the brewer " + (skills.getPotionBrewPoints() - pointsBefore)
                                    + " brew points, expected " + expected
                                    + " (this is the only live input to the Potions O.W.L.)");
                })
                .thenExecute(() -> retire(helper, brewer))
                .thenSucceed();
    }

    // ── the pot goes cold ───────────────────────────────────────────────────────────────────────

    /**
     * Heat is checked every tick, not just at the start. Losing the fire has to end the brew, or the fire
     * is paperwork: light it once, walk away, come back to a potion.
     */
    private static void withoutHeatSpoils(GameTestHelper helper) {
        ServerPlayer brewer = WizardTestSupport.placeMockPlayer(helper, "wandb-test-brewer");
        WizardTestSupport.parkAtOrigin(helper, brewer);

        CauldronBlockEntity pot = placeHeatedCauldron(helper);
        pot.addIngredient(new ItemStack(Items.BLAZE_POWDER));
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.setFilled(true);
        CauldronBlockEntity.StartResult start = pot.startBrewing(CauldronTier.PEWTER, brewer);
        check(helper, start == CauldronBlockEntity.StartResult.STARTED,
                () -> "startBrewing refused a valid Pepperup: " + start);

        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    check(helper, pot.phase() == CauldronPhase.BREWING,
                            () -> "the pot was not brewing before the fire went out, phase is " + pot.phase());
                    helper.setBlock(HEAT, Blocks.AIR);
                })
                // The pot is forgiving for a while, and that window is the rule rather than a bug: losing
                // the fire for a moment must not destroy a long brew. Asserted before the spoil, so a
                // cauldron that died instantly would fail here rather than silently pass below.
                .thenExecuteAfter(CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT / 2, () -> {
                    check(helper, pot.phase() == CauldronPhase.BREWING,
                            () -> "the pot spoiled inside its recoverable window; phase is " + pot.phase()
                                    + " only half of " + CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT
                                    + " ticks after the fire went out");
                })
                .thenExecuteAfter(CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT / 2 + 5, () -> {
                    check(helper, pot.phase() == CauldronPhase.SPOILED,
                            () -> "a pot left off the boil past " + CauldronBlockEntity.SPOIL_TICKS_WITHOUT_HEAT
                                    + " ticks did not spoil: phase is " + pot.phase()
                                    + ", remaining " + pot.remainingTicks());
                    pot.discardSpoiled();
                    check(helper, pot.phase() == CauldronPhase.IDLE,
                            () -> "discarding a spoiled batch did not return the pot to idle, phase is "
                                    + pot.phase());
                })
                .thenExecute(() -> retire(helper, brewer))
                .thenSucceed();
    }

    // ── setup ───────────────────────────────────────────────────────────────────────────────────

    /** A pewter cauldron sitting on a magma block, which is the simplest thing {@code CauldronHeat} accepts. */
    private static CauldronBlockEntity placeHeatedCauldron(GameTestHelper helper) {
        helper.setBlock(HEAT, Blocks.MAGMA_BLOCK);
        helper.setBlock(CAULDRON, ModBlocks.PEWTER_CAULDRON.get());
        BlockEntity blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(CAULDRON));
        if (!(blockEntity instanceof CauldronBlockEntity pot)) {
            helper.fail("no CauldronBlockEntity at the placed pewter cauldron; found "
                    + (blockEntity == null ? "nothing" : blockEntity.getClass().getSimpleName()));
            throw new IllegalStateException("unreachable");
        }
        return pot;
    }
}
