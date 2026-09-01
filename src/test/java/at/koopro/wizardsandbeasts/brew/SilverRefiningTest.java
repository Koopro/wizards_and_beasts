package at.koopro.wizardsandbeasts.brew;

import at.koopro.wizardsandbeasts.brew.def.BrewDefinition;
import at.koopro.wizardsandbeasts.brew.silver.SilveringHandler;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The silver chain, as far as it can be walked without a cauldron.
 *
 * <p>What is worth pinning here is the <em>definition</em> of silver: a brew is silver-based because
 * it names what it refines into, and a brew is pure silver because something else names it. Both are
 * derived from the data rather than declared, so both can be broken by an innocent-looking edit to
 * one JSON field.
 */
class SilverRefiningTest {

    private static final Gson GSON = new Gson();

    @BeforeEach
    @AfterEach
    void clearRegistry() {
        Brews.clear();
    }

    private static Brew silverBased(String id, String variantId) {
        return new Brew(id, "Silver", 0xC0C0C0, List.of(), null, variantId);
    }

    private static Brew plain(String id) {
        return new Brew(id, "Plain", 0, List.of(), null);
    }

    @Test
    void aBrewIsSilverBasedExactlyWhenItNamesAVariant() {
        assertTrue(silverBased("test:silver", "test:pure").isSilverBased());
        assertFalse(plain("test:plain").isSilverBased());
        assertFalse(silverBased("test:blank", "").isSilverBased(),
                "an empty variant id is not a declaration");
    }

    @Test
    void theFiveArgumentFormStillMeansNotSilverBased() {
        // The compact constructor exists so adding silver did not churn every call site; if it ever
        // starts defaulting to something else, brews all over the mod silently become refinable.
        Brew legacy = new Brew("test:legacy", "Legacy", 0, List.of(), "flavour");
        assertNull(legacy.silverVariant());
        assertFalse(legacy.isSilverBased());
    }

    @Test
    void variantOfResolvesThroughTheRegistry() {
        Brew pure = Brews.register(plain("test:pure"));
        Brew silver = Brews.register(silverBased("test:silver", "test:pure"));

        assertEquals(pure, SilverRefining.variantOf(silver));
        assertNull(SilverRefining.variantOf(plain("test:plain")), "a plain brew refines into nothing");
        assertNull(SilverRefining.variantOf(null));
    }

    @Test
    void aVariantThatWasNeverLoadedResolvesToNothingRatherThanThrowing() {
        // A datapack typo must be a message to the player, not a crash in a cauldron tick.
        Brew dangling = Brews.register(silverBased("test:silver", "test:missing"));
        assertTrue(dangling.isSilverBased());
        assertNull(SilverRefining.variantOf(dangling));
    }

    @Test
    void pureSilverIsWhateverSomethingElseRefinesInto() {
        Brews.register(plain("test:pure"));
        Brews.register(silverBased("test:silver", "test:pure"));

        assertTrue(SilveringHandler.isPureSilverBrew("test:pure"));
        assertFalse(SilveringHandler.isPureSilverBrew("test:silver"),
                "the coarse solution is not the refined one");
        assertFalse(SilveringHandler.isPureSilverBrew("test:unrelated"));
        assertFalse(SilveringHandler.isPureSilverBrew(null));
    }

    @Test
    void nothingIsPureSilverWhenNoBrewClaimsIt() {
        Brews.register(plain("test:pure"));
        assertFalse(SilveringHandler.isPureSilverBrew("test:pure"),
                "a brew nobody refines into is just a brew");
    }

    @Test
    void theSilverFieldSurvivesTheJsonRoundTrip() {
        BrewDefinition def = new BrewDefinition("Silver", 0xC0C0C0, List.of(),
                Optional.of("flavour"), Optional.of("wizards_and_beasts:pure_silver_solution"));

        JsonElement json = BrewDefinition.CODEC.encodeStart(JsonOps.INSTANCE, def)
                .getOrThrow(m -> new AssertionError("encode failed: " + m));
        BrewDefinition round = BrewDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(m -> new AssertionError("decode failed: " + m));

        assertEquals(def, round, "round trip changed the definition: " + GSON.toJson(json));
    }

    @Test
    void aBrewWrittenBeforeSilverExistedStillParses() {
        // silverVariant is optional precisely so the two brews that shipped before this feature, and
        // every datapack brew in the wild, keep loading.
        JsonElement legacy = GSON.fromJson(
                "{\"displayName\":\"x\",\"color\":0,\"effects\":[{\"id\":\"minecraft:luck\",\"duration\":20}]}",
                JsonElement.class);
        BrewDefinition parsed = BrewDefinition.CODEC.parse(JsonOps.INSTANCE, legacy)
                .getOrThrow(m -> new AssertionError("legacy brew failed to load: " + m));

        assertTrue(parsed.silverVariant().isEmpty());
        assertNotNull(parsed.toBrew("test:legacy"));
        assertFalse(parsed.toBrew("test:legacy").isSilverBased());
    }

    @Test
    void bakingAndUnbakingKeepsTheVariant() {
        BrewDefinition def = new BrewDefinition("Silver", 0xC0C0C0,
                List.of(new BrewDefinition.EffectEntry(
                        net.minecraft.resources.Identifier.withDefaultNamespace("luck"), 20, 0, false)),
                Optional.empty(), Optional.of("test:pure"));

        Brew baked = def.toBrew("test:silver");
        assertNotNull(baked);
        assertEquals("test:pure", baked.silverVariant());
        assertEquals(Optional.of("test:pure"), BrewDefinition.fromBrew(baked).silverVariant());
    }
}
