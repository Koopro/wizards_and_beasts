package at.koopro.wizardsandbeasts.ability.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Per-ability proficiency: clamping, zero pruning, and codec round-tripping. */
class PlayerAbilityProficiencyTest {

    private static final Gson GSON = new Gson();

    private static final Identifier APPARITION =
            Identifier.fromNamespaceAndPath("wizards_and_beasts", "apparition");
    private static final Identifier LEGILIMENCY =
            Identifier.fromNamespaceAndPath("wizards_and_beasts", "legilimency");

    @Test
    void unpractisedAbilitiesReadAsZero() {
        assertEquals(0.0f, PlayerAbilityProficiency.EMPTY.get(APPARITION), 1e-6);
    }

    @Test
    void valuesAreClampedToTheUnitRange() {
        assertEquals(1.0f, PlayerAbilityProficiency.EMPTY.with(APPARITION, 4.0f).get(APPARITION), 1e-6);
        assertEquals(0.0f, PlayerAbilityProficiency.EMPTY.with(APPARITION, -4.0f).get(APPARITION), 1e-6);
    }

    @Test
    void zeroAndNegativeEntriesArePrunedRatherThanStored() {
        Map<Identifier, Float> raw = new HashMap<>();
        raw.put(APPARITION, 0.0f);
        raw.put(LEGILIMENCY, -1.0f);

        assertTrue(new PlayerAbilityProficiency(raw).values().isEmpty(),
                "an untouched ability and one practised back to nothing must serialise identically");
    }

    @Test
    void nanIsDiscardedRatherThanPersisted() {
        Map<Identifier, Float> raw = new HashMap<>();
        raw.put(APPARITION, Float.NaN);

        assertFalse(new PlayerAbilityProficiency(raw).values().containsKey(APPARITION));
    }

    @Test
    void additionAccumulatesAndSaturatesAtMastery() {
        PlayerAbilityProficiency data = PlayerAbilityProficiency.EMPTY
                .plus(APPARITION, 0.4f)
                .plus(APPARITION, 0.4f);
        assertEquals(0.8f, data.get(APPARITION), 1e-6);

        assertEquals(1.0f, data.plus(APPARITION, 0.9f).get(APPARITION), 1e-6);
    }

    @Test
    void abilitiesDoNotBleedIntoEachOther() {
        PlayerAbilityProficiency data = PlayerAbilityProficiency.EMPTY.with(APPARITION, 0.6f);
        assertEquals(0.0f, data.get(LEGILIMENCY), 1e-6);
    }

    @Test
    void forgettingAnUnknownAbilityChangesNothing() {
        PlayerAbilityProficiency data = PlayerAbilityProficiency.EMPTY.with(APPARITION, 0.5f);
        assertSame(data, data.without(LEGILIMENCY));
        assertTrue(data.without(APPARITION).values().isEmpty());
    }

    @Test
    void proficiencySurvivesACodecRoundTrip() {
        PlayerAbilityProficiency original = PlayerAbilityProficiency.EMPTY
                .with(APPARITION, 0.75f)
                .with(LEGILIMENCY, 0.25f);

        JsonElement encoded = PlayerAbilityProficiency.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerAbilityProficiency restored = PlayerAbilityProficiency.CODEC
                .parse(JsonOps.INSTANCE, GSON.fromJson(GSON.toJson(encoded), JsonElement.class))
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(0.75f, restored.get(APPARITION), 1e-6);
        assertEquals(0.25f, restored.get(LEGILIMENCY), 1e-6);
        assertEquals(original, restored);
    }

    @Test
    void anEmptyStoreRoundTripsToEmpty() {
        JsonElement encoded = PlayerAbilityProficiency.CODEC
                .encodeStart(JsonOps.INSTANCE, PlayerAbilityProficiency.EMPTY)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
        PlayerAbilityProficiency restored = PlayerAbilityProficiency.CODEC
                .parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));

        assertEquals(PlayerAbilityProficiency.EMPTY, restored);
    }
}
