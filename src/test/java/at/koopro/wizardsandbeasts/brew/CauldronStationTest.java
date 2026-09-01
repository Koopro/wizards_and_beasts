package at.koopro.wizardsandbeasts.brew;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The station's rules, as far as they can be checked without a world.
 *
 * <p>The block entity itself needs a level to construct, so what is pinned here is the layer beneath
 * it: the tier ladder that decides which pot can hold what, and recipe matching against a
 * <b>container</b> rather than a player's inventory. That second one is the whole reason this slice
 * exists — brewing used to search the brewer's backpack, so a cauldron could start a recipe out of
 * items that had never been in it.
 */
class CauldronStationTest {

    private SimpleContainer pot;

    @BeforeEach
    void freshPot() {
        pot = new SimpleContainer(CauldronContainerFixture.SLOTS);
        BrewingRecipes.clear();
    }

    /** Slot count mirror, so the fixture does not depend on the block entity class loading. */
    private static final class CauldronContainerFixture {
        static final int SLOTS = 6;
    }

    private static BrewingRecipe recipe(String id, CauldronTier tier,
                                        List<BrewingRecipe.Ingredient> ingredients) {
        return new BrewingRecipe(id, ingredients, tier, 200, "wizards_and_beasts:test_brew");
    }

    private static BrewingRecipe.Ingredient ing(net.minecraft.world.item.Item item, int count) {
        return new BrewingRecipe.Ingredient(item, count);
    }

    // ── the ladder ─────────────────────────────────────────────────────────────────────────

    @Test
    void pewterIsTheStudentCauldronAndSitsAtTheBottom() {
        // The list Harry is sent: "1 standard size 2 pewter cauldron". It used to be the top tier,
        // which had brass and copper - the upgrades - gating easier recipes than the starter pot.
        assertEquals(0, CauldronTier.PEWTER.ordinal());
        assertTrue(CauldronTier.COPPER.isAtLeast(CauldronTier.PEWTER));
        assertFalse(CauldronTier.PEWTER.isAtLeast(CauldronTier.COPPER));
    }

    @Test
    void everyTierSatisfiesItself() {
        for (CauldronTier tier : CauldronTier.values()) {
            assertTrue(tier.isAtLeast(tier), tier + " must satisfy its own requirement");
        }
    }

    @Test
    void brewPointsFollowTheLadderRatherThanBeingWrittenOut() {
        // Derived from the ordinal, so a new tier cannot be added without a weight and the two can
        // never disagree about which pot is harder.
        assertEquals(1, CauldronTier.PEWTER.brewPoints());
        assertEquals(2, CauldronTier.BRASS.brewPoints());
        assertEquals(3, CauldronTier.COPPER.brewPoints());
    }

    @Test
    void tierNamesRoundTripThroughTheirSerialisedForm() {
        // Recipe JSON stores the name; a rename that broke this would silently ungate every recipe.
        for (CauldronTier tier : CauldronTier.values()) {
            assertEquals(tier, CauldronTier.CODEC
                    .parse(com.mojang.serialization.JsonOps.INSTANCE,
                            new com.google.gson.JsonPrimitive(tier.getSerializedName()))
                    .getOrThrow());
        }
    }

    // ── matching against the pot ───────────────────────────────────────────────────────────

    @Test
    void aRecipeMatchesWhenThePotHoldsItsIngredients() {
        BrewingRecipe r = recipe("test:one", CauldronTier.PEWTER,
                List.of(ing(Items.SUGAR, 1), ing(Items.APPLE, 2)));
        pot.setItem(0, new ItemStack(Items.SUGAR, 1));
        pot.setItem(1, new ItemStack(Items.APPLE, 2));

        assertTrue(r.matches(pot, CauldronTier.PEWTER));
    }

    @Test
    void countsAreSummedAcrossSlots() {
        BrewingRecipe r = recipe("test:two", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 3)));
        pot.setItem(0, new ItemStack(Items.APPLE, 1));
        pot.setItem(3, new ItemStack(Items.APPLE, 2));

        assertTrue(r.matches(pot, CauldronTier.PEWTER));
    }

    @Test
    void aShortCountDoesNotMatch() {
        BrewingRecipe r = recipe("test:three", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 3)));
        pot.setItem(0, new ItemStack(Items.APPLE, 2));

        assertFalse(r.matches(pot, CauldronTier.PEWTER));
    }

    @Test
    void aPotBelowTheRequiredTierDoesNotMatch() {
        BrewingRecipe r = recipe("test:four", CauldronTier.COPPER, List.of(ing(Items.APPLE, 1)));
        pot.setItem(0, new ItemStack(Items.APPLE, 1));

        assertFalse(r.matches(pot, CauldronTier.PEWTER), "a student pot must not brew a copper recipe");
        assertTrue(r.matches(pot, CauldronTier.COPPER));
    }

    @Test
    void consumingTakesExactlyTheRecipeAmountAndLeavesTheRest() {
        BrewingRecipe r = recipe("test:five", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 3)));
        pot.setItem(0, new ItemStack(Items.APPLE, 5));

        r.consumeFrom(pot);

        assertEquals(2, pot.getItem(0).getCount(), "surplus ingredients stay in the pot");
    }

    @Test
    void consumingDrainsAcrossSlots() {
        BrewingRecipe r = recipe("test:six", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 3)));
        pot.setItem(0, new ItemStack(Items.APPLE, 1));
        pot.setItem(2, new ItemStack(Items.APPLE, 2));

        r.consumeFrom(pot);

        assertTrue(pot.getItem(0).isEmpty());
        assertTrue(pot.getItem(2).isEmpty());
    }

    @Test
    void consumingActuallyEmptiesTheContainer() {
        // The bug this guards: consumeFrom used to shrink() the stack it was handed. A Container is
        // entitled to hand out copies, and shrinking a copy consumes nothing - the pot would keep its
        // ingredients and every brew would be free.
        BrewingRecipe r = recipe("test:seven", CauldronTier.PEWTER, List.of(ing(Items.SUGAR, 1)));
        pot.setItem(0, new ItemStack(Items.SUGAR, 1));

        r.consumeFrom(pot);

        assertTrue(pot.getItem(0).isEmpty(), "the ingredient must actually leave the pot");
    }

    // ── the registry search ────────────────────────────────────────────────────────────────

    @Test
    void findMatchReadsOnlyTheContainerItIsGiven() {
        // The acceptance criterion for this slice. The pot is empty; a backpack elsewhere holding the
        // ingredients is not this container and must not produce a match.
        BrewingRecipes.register(recipe("test:eight", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 1))));
        SimpleContainer backpack = new SimpleContainer(36);
        backpack.setItem(0, new ItemStack(Items.APPLE, 64));

        assertNull(BrewingRecipes.findMatch(pot, CauldronTier.PEWTER),
                "an empty cauldron must match nothing, however full the brewer's bag is");
        assertNotNull(BrewingRecipes.findMatch(backpack, CauldronTier.PEWTER),
                "the search itself works - it is which container it is pointed at that changed");
    }

    @Test
    void noMatchWhenTheTierIsTooLow() {
        BrewingRecipes.register(recipe("test:nine", CauldronTier.COPPER, List.of(ing(Items.APPLE, 1))));
        pot.setItem(0, new ItemStack(Items.APPLE, 1));

        assertNull(BrewingRecipes.findMatch(pot, CauldronTier.PEWTER));
        assertNull(BrewingRecipes.findMatch(pot, CauldronTier.BRASS));
        assertNotNull(BrewingRecipes.findMatch(pot, CauldronTier.COPPER));
    }

    @Test
    void twoPotsAreIndependent() {
        BrewingRecipes.register(recipe("test:ten", CauldronTier.PEWTER, List.of(ing(Items.APPLE, 1))));
        SimpleContainer other = new SimpleContainer(CauldronContainerFixture.SLOTS);
        pot.setItem(0, new ItemStack(Items.APPLE, 1));

        assertNotNull(BrewingRecipes.findMatch(pot, CauldronTier.PEWTER));
        assertNull(BrewingRecipes.findMatch(other, CauldronTier.PEWTER),
                "one cauldron's contents must not satisfy another's recipe search");
    }

    // ── phases ─────────────────────────────────────────────────────────────────────────────

    @Test
    void onlyAnIdleCauldronTakesIngredients() {
        assertTrue(CauldronPhase.IDLE.acceptsIngredients());
        assertFalse(CauldronPhase.BREWING.acceptsIngredients());
        assertFalse(CauldronPhase.DONE.acceptsIngredients(),
                "a finished pot must be bottled before it is reused, not topped up");
        assertFalse(CauldronPhase.SPOILED.acceptsIngredients());
    }

    @Test
    void onlyBrewingRunsTheClock() {
        for (CauldronPhase phase : CauldronPhase.values()) {
            assertEquals(phase == CauldronPhase.BREWING, phase.isActive(), phase.toString());
        }
    }

    @Test
    void phaseNamesRoundTripAndUnknownNamesFallBack() {
        for (CauldronPhase phase : CauldronPhase.values()) {
            assertEquals(phase, CauldronPhase.byName(phase.getSerializedName(), CauldronPhase.SPOILED));
        }
        // A save from a future version naming a phase this build has never heard of must land
        // somewhere harmless rather than throwing during block entity load.
        assertEquals(CauldronPhase.IDLE, CauldronPhase.byName("fermenting", CauldronPhase.IDLE));
    }
}
