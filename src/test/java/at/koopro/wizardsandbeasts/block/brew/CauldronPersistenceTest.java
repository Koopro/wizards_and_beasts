package at.koopro.wizardsandbeasts.block.brew;

import at.koopro.wizardsandbeasts.brew.BrewingRecipe;
import at.koopro.wizardsandbeasts.brew.BrewingRecipes;
import at.koopro.wizardsandbeasts.brew.Brews;
import at.koopro.wizardsandbeasts.brew.Brew;
import at.koopro.wizardsandbeasts.brew.CauldronPhase;
import at.koopro.wizardsandbeasts.brew.CauldronTier;
import at.koopro.wizardsandbeasts.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A brew survives a restart, and two pots are two pots.
 *
 * <p>This pins the failure the whole station rewrite existed for: the old {@code static ACTIVE_BREWS}
 * map was never saved, so a restart mid-brew destroyed the brew <em>after</em> eating the
 * ingredients. Nothing crashed and nothing logged — the player simply never got a bottle.
 *
 * <h2>Why this is a unit test when the spoil threshold is not</h2>
 * <p>{@code serverTick} needs a {@link net.minecraft.server.level.ServerLevel}: it reads the block
 * below for heat and plays sounds. Save and load need <b>no level at all</b> — they are a block
 * entity writing its own fields to a tag — so driving them directly is the honest test of
 * persistence, and it catches what actually breaks: a field added to one method and forgotten in the
 * other.
 *
 * <p>In the same package as the block entity so the real save/load entry points are reachable without
 * adding test-only accessors to production code.
 */
class CauldronPersistenceTest {

    private static final RegistryAccess REGISTRIES =
            RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    private static final String BREW_ID = "test:persist_brew";

    @BeforeEach
    void seedRegistries() {
        BrewingRecipes.clear();
        Brews.clear();
        Brews.register(new Brew(BREW_ID, "brew.test.name", 0xFF00FF00,
                List.of(), null, null,
                List.of(at.koopro.wizardsandbeasts.brew.effect.BrewEffectEntry.onDrink(
                        new at.koopro.wizardsandbeasts.brew.effect.BrewEffect.Flourish(
                                4, java.util.Optional.empty())))));
        BrewingRecipes.register(new BrewingRecipe("test:persist_recipe",
                List.of(new BrewingRecipe.Ingredient(Items.SUGAR, 1),
                        new BrewingRecipe.Ingredient(Items.APPLE, 1)),
                CauldronTier.PEWTER, 200, BREW_ID));
    }

    private static CauldronBlockEntity newCauldron() {
        BlockState state = ModBlocks.PEWTER_CAULDRON.get().defaultBlockState();
        return new CauldronBlockEntity(BlockPos.ZERO, state);
    }

    /** Round-trips a cauldron through NBT, the way a chunk save and reload does. */
    private static CauldronBlockEntity roundTrip(CauldronBlockEntity original) {
        CompoundTag tag = original.saveWithoutMetadata(REGISTRIES);
        CauldronBlockEntity loaded = newCauldron();
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(LoggerFactory.getLogger("cauldron-test"))) {
            ValueInput in = TagValueInput.create(reporter, REGISTRIES, tag);
            loaded.loadCustomOnly(in);
        }
        return loaded;
    }

    /** A pot part-way through the seeded recipe, started by a brewer who is not online. */
    private static CauldronBlockEntity brewingPot() {
        CauldronBlockEntity pot = newCauldron();
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.addIngredient(new ItemStack(Items.APPLE));
        assertEquals(CauldronBlockEntity.StartResult.STARTED,
                pot.startBrewing(CauldronTier.PEWTER, UUID.randomUUID()));
        return pot;
    }

    // ── the restart ────────────────────────────────────────────────────────────────────────

    @Test
    void aBrewInProgressSurvivesARoundTrip() {
        CauldronBlockEntity pot = brewingPot();
        assertEquals(200, pot.remainingTicks());

        CauldronBlockEntity loaded = roundTrip(pot);

        assertEquals(CauldronPhase.BREWING, loaded.phase());
        assertEquals(200, loaded.remainingTicks(), "the clock must not reset across a restart");
        assertEquals(200, loaded.totalTicks());
        assertEquals(BREW_ID, loaded.brewId());
        assertTrue(loaded.isFilled(), "the water must still be in the pot");
        assertTrue(loaded.brewerId().isPresent(), "the OWL credit must survive to completion");
    }

    @Test
    void startingConsumesFromThePotAndTheEmptinessPersists() {
        CauldronBlockEntity pot = brewingPot();
        assertTrue(pot.isEmptyOfIngredients(), "starting must actually eat the ingredients");
        assertTrue(roundTrip(pot).isEmptyOfIngredients());
    }

    @Test
    void ingredientsInAnIdlePotSurviveARoundTrip() {
        CauldronBlockEntity pot = newCauldron();
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.addIngredient(new ItemStack(Items.APPLE));

        CauldronBlockEntity loaded = roundTrip(pot);

        assertEquals(2, loaded.usedSlots(), "two kinds of ingredient, merged into two slots");
        assertFalse(loaded.isEmptyOfIngredients());
    }

    @Test
    void anIdleEmptyPotRoundTripsToIdleAndEmpty() {
        CauldronBlockEntity loaded = roundTrip(newCauldron());

        assertEquals(CauldronPhase.IDLE, loaded.phase());
        assertTrue(loaded.isEmptyOfIngredients());
        assertFalse(loaded.isFilled());
        assertNull(loaded.brewId());
        assertEquals(0, loaded.remainingTicks());
    }

    @Test
    void aFinishedPotStillHoldsItsBrewAfterARestart() {
        // DONE holds a brew nobody has bottled yet. Losing it on restart destroys a finished potion,
        // which is the same class of bug as losing one mid-brew.
        CauldronBlockEntity pot = brewingPot();
        CompoundTag tag = pot.saveWithoutMetadata(REGISTRIES);
        tag.putString("phase", CauldronPhase.DONE.getSerializedName());
        tag.putInt("remainingTicks", 0);

        CauldronBlockEntity loaded = newCauldron();
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(LoggerFactory.getLogger("cauldron-test"))) {
            loaded.loadCustomOnly(TagValueInput.create(reporter, REGISTRIES, tag));
        }

        assertEquals(CauldronPhase.DONE, loaded.phase());
        assertEquals(BREW_ID, loaded.brewId());
    }

    @Test
    void aBrewWhoseRecipeVanishedLoadsAsSpoiledRatherThanTickingForever() {
        // Datapacks change between sessions. A BREWING pot pointing at a brew id that no longer
        // resolves can never finish, so loading it as BREWING would tick towards a bottle that
        // cannot be made.
        CauldronBlockEntity pot = brewingPot();
        CompoundTag tag = pot.saveWithoutMetadata(REGISTRIES);
        Brews.clear(); // the datapack that defined it is gone

        CauldronBlockEntity loaded = newCauldron();
        try (ProblemReporter.ScopedCollector reporter =
                     new ProblemReporter.ScopedCollector(LoggerFactory.getLogger("cauldron-test"))) {
            loaded.loadCustomOnly(TagValueInput.create(reporter, REGISTRIES, tag));
        }

        assertEquals(CauldronPhase.SPOILED, loaded.phase());
        assertNull(loaded.brewId());
    }

    // ── two pots ───────────────────────────────────────────────────────────────────────────

    @Test
    void twoCauldronsDoNotShareState() {
        // The static-map bug in one sentence: state keyed globally rather than held per block.
        CauldronBlockEntity a = brewingPot();
        CauldronBlockEntity b = newCauldron();

        assertEquals(CauldronPhase.BREWING, a.phase());
        assertEquals(CauldronPhase.IDLE, b.phase());
        assertTrue(b.isEmptyOfIngredients());
        assertFalse(b.isFilled());
        assertNull(b.brewId());
        assertNotEquals(a.remainingTicks(), b.remainingTicks());
    }

    @Test
    void oneCauldronsIngredientsDoNotSatisfyAnothersStart() {
        CauldronBlockEntity stocked = newCauldron();
        stocked.setFilled(true);
        stocked.addIngredient(new ItemStack(Items.SUGAR));
        stocked.addIngredient(new ItemStack(Items.APPLE));

        CauldronBlockEntity empty = newCauldron();
        empty.setFilled(true);

        assertEquals(CauldronBlockEntity.StartResult.EMPTY,
                empty.startBrewing(CauldronTier.PEWTER, UUID.randomUUID()),
                "an empty pot must not start on its neighbour's ingredients");
    }

    // ── the interaction guard ──────────────────────────────────────────────────────────────

    @Test
    void aBrandNewCauldronAcceptsItsVeryFirstInteraction() {
        // The bug this exists for: lastInteractGameTime started at Long.MIN_VALUE and the guard did
        // , which OVERFLOWS to a large negative number — less than
        // the gap, so the first interaction was refused. It returned without stamping, so the next
        // one overflowed identically. Every cauldron refused water, ingredients and bottles forever,
        // silently. The field is not persisted, so a reload put every pot back into it.
        assertTrue(newCauldron().acceptInteraction(1000L, 4),
                "a pot nobody has touched must accept the first click");
    }

    @Test
    void theFirstInteractionIsAcceptedAtAnyGameTime() {
        for (long time : new long[]{0L, 1L, 20L, 1_000L, 1_000_000L, Long.MAX_VALUE / 2}) {
            assertTrue(newCauldron().acceptInteraction(time, 4), "refused at gameTime " + time);
        }
    }

    @Test
    void aSecondInteractionInsideTheGapIsRefused() {
        CauldronBlockEntity pot = newCauldron();
        assertTrue(pot.acceptInteraction(1000L, 4));
        assertFalse(pot.acceptInteraction(1002L, 4), "held right-click must not feed the pot twice");
    }

    @Test
    void anInteractionAfterTheGapIsAccepted() {
        CauldronBlockEntity pot = newCauldron();
        assertTrue(pot.acceptInteraction(1000L, 4));
        assertTrue(pot.acceptInteraction(1004L, 4));
    }

    @Test
    void aClockThatWentBackwardsDoesNotBarThePlayer() {
        // World restore or rollback. Barring somebody until the clock catches up could mean hours.
        CauldronBlockEntity pot = newCauldron();
        assertTrue(pot.acceptInteraction(1_000_000L, 4));
        assertTrue(pot.acceptInteraction(500L, 4));
    }

    // ── the start gate ─────────────────────────────────────────────────────────────────────

    @Test
    void anUnfilledPotRefusesToStart() {
        CauldronBlockEntity pot = newCauldron();
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.SUGAR));
        pot.addIngredient(new ItemStack(Items.APPLE));
        pot.setFilled(false);

        assertEquals(CauldronBlockEntity.StartResult.NOT_FILLED,
                pot.startBrewing(CauldronTier.PEWTER, UUID.randomUUID()));
    }

    @Test
    void ingredientsThatMatchNoRecipeAreRefusedAndNotEaten() {
        CauldronBlockEntity pot = newCauldron();
        pot.setFilled(true);
        pot.addIngredient(new ItemStack(Items.DIAMOND));

        assertEquals(CauldronBlockEntity.StartResult.NO_MATCH,
                pot.startBrewing(CauldronTier.PEWTER, UUID.randomUUID()));
        assertFalse(pot.isEmptyOfIngredients(), "a refused start must not consume the pot");
    }

    @Test
    void aSecondStartOnABrewingPotIsRefused() {
        CauldronBlockEntity pot = brewingPot();
        assertEquals(CauldronBlockEntity.StartResult.NOT_IDLE,
                pot.startBrewing(CauldronTier.PEWTER, UUID.randomUUID()));
    }
}
