package at.koopro.wizardsandbeasts.polyjuice;

import at.koopro.wizardsandbeasts.brew.effect.BrewEffect;
import at.koopro.wizardsandbeasts.disguise.DisguiseState;
import at.koopro.wizardsandbeasts.registry.ConsumableItemRegistry;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The identity pipeline: what a sample is, what a disguise is, and what neither of them is.
 *
 * <p>The rendering half needs a client and the transform half needs a level, so what is pinned here
 * is the data — the sample on the bottle, the state on the player, and the component that connects
 * them. Those are where a mistake would be a <em>security</em> mistake rather than a visual one.
 */
class PolyjuiceTest {

    private static final UUID TARGET = UUID.fromString("11111111-2222-3333-4444-555555555555");

    // ── the sample on the bottle ───────────────────────────────────────────────────────────

    private static ItemStack bottle() {
        return new ItemStack(ConsumableItemRegistry.BREW.get());
    }

    @Test
    void aSampleRoundTripsThroughTheComponent() {
        ItemStack stack = bottle();
        PolyjuiceSample.write(stack, TARGET, "Hermione");

        PolyjuiceSample read = PolyjuiceSample.read(stack);

        assertTrue(read.isPresent());
        assertEquals(Optional.of(TARGET), read.id());
        assertEquals("Hermione", read.name());
    }

    @Test
    void aBottleWithNoComponentHasNoSample() {
        assertFalse(PolyjuiceSample.read(bottle()).isPresent());
        assertEquals(PolyjuiceSample.NONE, PolyjuiceSample.read(bottle()));
    }

    @Test
    void aBottleOfSomethingElseHasNoSample() {
        assertFalse(PolyjuiceSample.read(new ItemStack(Items.GLASS_BOTTLE)).isPresent());
    }

    @Test
    void aMalformedSampleReadsAsAbsentRatherThanThrowing() {
        // The component is persisted and synced, so it can arrive hand-edited, truncated, or written
        // by an older build. Every one of those must mean "this bottle lacks a sample", which the
        // drink path already knows how to say — not an exception inside somebody's inventory.
        for (String raw : new String[]{"", "   ", "not-a-uuid Hermione", "11111111-2222-3333-4444-555555555555",
                " Hermione", "11111111-2222-3333-4444-555555555555 "}) {
            ItemStack stack = bottle();
            stack.set(at.koopro.wizardsandbeasts.registry.ModDataComponents.POLYJUICE_TARGET.get(), raw);
            assertFalse(PolyjuiceSample.read(stack).isPresent(), "should be absent for: [" + raw + "]");
        }
    }

    @Test
    void aNameWithSpacesSurvives() {
        // Bedrock-style names and nicknames contain spaces; splitting on the first one keeps the
        // UUID exact and treats everything after it as the name.
        ItemStack stack = bottle();
        PolyjuiceSample.write(stack, TARGET, "The Half Blood Prince");
        assertEquals("The Half Blood Prince", PolyjuiceSample.read(stack).name());
    }

    // ── the state ──────────────────────────────────────────────────────────────────────────

    @Test
    void noStateMeansNoDisguise() {
        assertFalse(DisguiseState.NONE.isDisguised());
    }

    @Test
    void aStateWithTicksButNoTargetIsNotADisguise() {
        // Both halves are required. A timer with nobody to look like would render as the player's own
        // face while the server believed they were hidden — the worst of both.
        assertFalse(new DisguiseState(200, Optional.empty(), "Hermione").isDisguised());
    }

    @Test
    void aStateWithATargetButNoTicksIsNotADisguise() {
        assertFalse(new DisguiseState(0, Optional.of(TARGET), "Hermione").isDisguised());
    }

    @Test
    void aFullStateIsADisguise() {
        assertTrue(new DisguiseState(200, Optional.of(TARGET), "Hermione").isDisguised());
    }

    @Test
    void tickingDownToZeroEndsIt() {
        DisguiseState state = new DisguiseState(1, Optional.of(TARGET), "Hermione");
        assertFalse(state.withTicks(0).isDisguised());
    }

    @Test
    void tickingNeverGoesNegative() {
        assertEquals(0, new DisguiseState(1, Optional.of(TARGET), "H").withTicks(-9).ticksRemaining());
    }

    @Test
    void anIndefiniteDisguiseIsADisguise() {
        // The admin command's shape. A negative sentinel rather than a huge number, so that "no clock"
        // is a state the countdown can recognise instead of one it decrements for sixty-eight years.
        assertTrue(new DisguiseState(DisguiseState.INDEFINITE, Optional.of(TARGET), "Hermione")
                .isDisguised());
    }

    @Test
    void anIndefiniteDisguiseNeverTicksDown() {
        DisguiseState state = new DisguiseState(DisguiseState.INDEFINITE, Optional.of(TARGET), "H");
        assertEquals(DisguiseState.INDEFINITE, state.tickDown().tickDown().ticksRemaining());
        assertTrue(state.isIndefinite());
    }

    @Test
    void aTimedDisguiseCannotTickIntoTheIndefiniteSentinel() {
        // The sentinel is -1 and the clock counts down, so the one tick that matters is the one at
        // zero. If it ever passed through, an expiring Polyjuice would silently become permanent.
        DisguiseState expiring = new DisguiseState(1, Optional.of(TARGET), "H");
        DisguiseState ended = expiring.tickDown();
        assertEquals(0, ended.ticksRemaining());
        assertFalse(ended.isDisguised());
        assertFalse(ended.tickDown().isIndefinite());
    }

    @Test
    void theStateCarriesNothingThatCouldGrantAnything() {
        // The security property, asserted structurally: a disguise is an appearance and a clock. If a
        // future field ever added a permission, a team or a UUID claim, this is the test that should
        // stop it — the record has exactly three components and none of them is an identity.
        assertEquals(3, DisguiseState.class.getRecordComponents().length,
                "a disguise must remain appearance + clock; anything else is an identity claim");
    }

    // ── the component ──────────────────────────────────────────────────────────────────────

    private static BrewEffect parse(String json) {
        return BrewEffect.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void theComponentParsesAndDefaults() {
        BrewEffect.PolyjuiceDisguise explicit = assertInstanceOf(BrewEffect.PolyjuiceDisguise.class,
                parse("{\"type\":\"polyjuice_disguise\",\"durationTicks\":1200}"));
        assertEquals(1200, explicit.durationTicks());

        BrewEffect.PolyjuiceDisguise bare = (BrewEffect.PolyjuiceDisguise)
                parse("{\"type\":\"polyjuice_disguise\"}");
        assertEquals(PolyjuiceService.DEFAULT_DURATION_TICKS, bare.durationTicks());
    }

    @Test
    void theComponentRoundTrips() {
        BrewEffect original = new BrewEffect.PolyjuiceDisguise(2400);
        var encoded = BrewEffect.CODEC.encodeStart(JsonOps.INSTANCE, original).getOrThrow();
        assertEquals(original, BrewEffect.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
    }

    @Test
    void theShippedBrewIsADisguiseAndNotInvisibility() throws Exception {
        // The acceptance criterion in data form.
        String json = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/data/wizards_and_beasts/brews/polyjuice_potion.json"));
        var doc = JsonParser.parseString(json).getAsJsonObject();

        boolean hasDisguise = false;
        for (var element : doc.getAsJsonArray("components")) {
            if ("polyjuice_disguise".equals(element.getAsJsonObject().get("type").getAsString())) {
                hasDisguise = true;
            }
        }
        assertTrue(hasDisguise, "Polyjuice must carry the disguise component");
        assertFalse(json.contains("minecraft:invisibility"),
                "Polyjuice must no longer be an Invisibility potion wearing a famous name");
    }
}
