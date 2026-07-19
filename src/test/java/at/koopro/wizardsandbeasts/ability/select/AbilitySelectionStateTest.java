package at.koopro.wizardsandbeasts.ability.select;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Locks the quick-slot model and — the risky part — the on-disk migration from the single {@code pinned}
 * field that saved worlds still carry.
 */
class AbilitySelectionStateTest {

    private static final Gson GSON = new Gson();

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    private static AbilitySelectionState parse(String json) {
        JsonElement element = GSON.fromJson(json, JsonElement.class);
        return AbilitySelectionState.CODEC.parse(JsonOps.INSTANCE, element)
                .getOrThrow(msg -> new AssertionError("parse failed: " + msg));
    }

    private static JsonElement encode(AbilitySelectionState state) {
        return AbilitySelectionState.CODEC.encodeStart(JsonOps.INSTANCE, state)
                .getOrThrow(msg -> new AssertionError("encode failed: " + msg));
    }

    // ── slot model ──

    @Test
    void quickSlotsBindAndClearIndependently() {
        AbilitySelectionState state = AbilitySelectionState.EMPTY
                .withQuickSlot(0, id("obscurus_surge"))
                .withQuickSlot(1, id("obscurus_grasp"));

        assertEquals(id("obscurus_surge"), state.quickSlot(0));
        assertEquals(id("obscurus_grasp"), state.quickSlot(1));
        assertNull(state.quickSlot(2));
        assertEquals(0, state.slotOf(id("obscurus_surge")));
        assertEquals(AbilitySelectionState.SLOT_SELECTED, state.slotOf(id("apparition")));

        AbilitySelectionState cleared = state.withQuickSlot(0, null);
        assertNull(cleared.quickSlot(0));
        assertEquals(id("obscurus_grasp"), cleared.quickSlot(1), "clearing one slot leaves the others");
    }

    @Test
    void anAbilityOccupiesAtMostOneSlot() {
        AbilitySelectionState state = AbilitySelectionState.EMPTY
                .withQuickSlot(0, id("obscurus_surge"))
                .withQuickSlot(2, id("obscurus_surge"));

        assertNull(state.quickSlot(0), "re-binding moves it rather than duplicating it");
        assertEquals(id("obscurus_surge"), state.quickSlot(2));
        assertEquals(2, state.slotOf(id("obscurus_surge")));
    }

    @Test
    void outOfRangeSlotsAreRejected() {
        assertTrue(AbilitySelectionState.isValidSlot(0));
        assertTrue(AbilitySelectionState.isValidSlot(AbilitySelectionState.QUICK_SLOT_COUNT - 1));
        assertTrue(!AbilitySelectionState.isValidSlot(AbilitySelectionState.QUICK_SLOT_COUNT));
        assertTrue(!AbilitySelectionState.isValidSlot(AbilitySelectionState.SLOT_SELECTED));

        AbilitySelectionState state = AbilitySelectionState.EMPTY
                .withQuickSlot(AbilitySelectionState.QUICK_SLOT_COUNT, id("apparition"));
        assertEquals(AbilitySelectionState.SLOT_SELECTED, state.slotOf(id("apparition")));
    }

    // ── persistence ──

    @Test
    void quickSlotsSurviveARoundTrip() {
        AbilitySelectionState original = AbilitySelectionState.EMPTY
                .withSelected(id("apparition"))
                .withQuickSlot(0, id("obscurus_surge"))
                .withQuickSlot(2, id("obscurial_form"))
                .withToggle(id("obscurial_form"), true)
                .withCooldown(id("apparition"), 1234L);

        AbilitySelectionState restored = parse(GSON.toJson(encode(original)));

        assertEquals(original.selected(), restored.selected());
        assertEquals(id("obscurus_surge"), restored.quickSlot(0));
        assertNull(restored.quickSlot(1));
        assertEquals(id("obscurial_form"), restored.quickSlot(2));
        assertTrue(restored.isToggled(id("obscurial_form")));
        assertEquals(1234L, restored.cooldowns().get(id("apparition")));
    }

    /** A world saved before quick slots existed carries a single {@code pinned} id. */
    @Test
    void legacyPinnedFieldBecomesTheFirstQuickSlot() {
        AbilitySelectionState state = parse("""
                {
                  "selected": "wizards_and_beasts:apparition",
                  "pinned": "wizards_and_beasts:legilimency"
                }
                """);

        assertEquals(id("legilimency"), state.quickSlot(0));
        assertEquals(0, state.slotOf(id("legilimency")));
        assertEquals(id("apparition"), state.selected());
    }

    @Test
    void explicitQuickSlotsWinOverTheLegacyPin() {
        AbilitySelectionState state = parse("""
                {
                  "quickSlots": [ { "slot": 0, "ability": "wizards_and_beasts:obscurus_surge" } ],
                  "pinned": "wizards_and_beasts:legilimency"
                }
                """);

        assertEquals(id("obscurus_surge"), state.quickSlot(0), "the new field is authoritative");
        assertEquals(AbilitySelectionState.SLOT_SELECTED, state.slotOf(id("legilimency")));
    }

    @Test
    void theLegacyPinIsNotWrittenBack() {
        AbilitySelectionState migrated = parse("{ \"pinned\": \"wizards_and_beasts:legilimency\" }");
        String json = GSON.toJson(encode(migrated));

        assertTrue(!json.contains("pinned"), "re-saving drops the legacy field: " + json);
        assertTrue(json.contains("quickSlots"), json);
    }
}
