package at.koopro.wizardsandbeasts.brew;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a cauldron shows, and that it shows something different for every condition worth seeing.
 *
 * <p>The projection is pure, which is the only reason it can be tested at all — the block entity that
 * feeds it needs a level. Keeping the rule as one expression is also what stops it drifting: a phase
 * added without a visual would fail to compile here rather than silently render as EMPTY.
 */
class CauldronVisualTest {

    @Test
    void anUnfilledIdlePotIsEmpty() {
        assertEquals(CauldronVisual.EMPTY, CauldronVisual.of(CauldronPhase.IDLE, false, false));
    }

    @Test
    void ingredientsInAnUnfilledPotStillReadAsEmpty() {
        // Cannot happen through the block — ingredients are refused until it is filled — but the
        // projection must not invent a state for it. Unfilled is unfilled.
        assertEquals(CauldronVisual.EMPTY, CauldronVisual.of(CauldronPhase.IDLE, false, true));
    }

    @Test
    void aFilledPotWithNothingInItIsWater() {
        assertEquals(CauldronVisual.WATER, CauldronVisual.of(CauldronPhase.IDLE, true, false));
    }

    @Test
    void aFilledPotWithSomethingInItIsIngredients() {
        assertEquals(CauldronVisual.INGREDIENTS, CauldronVisual.of(CauldronPhase.IDLE, true, true));
    }

    @Test
    void theWorkingPhasesMapStraightThrough() {
        // Ingredients are consumed at start, so the flags are false while brewing — the phase has to
        // win over them, or a working pot would show as WATER.
        assertEquals(CauldronVisual.BREWING, CauldronVisual.of(CauldronPhase.BREWING, true, false));
        assertEquals(CauldronVisual.DONE, CauldronVisual.of(CauldronPhase.DONE, true, false));
        assertEquals(CauldronVisual.SPOILED, CauldronVisual.of(CauldronPhase.SPOILED, true, false));
    }

    @Test
    void everyPhaseProjectsToSomething() {
        for (CauldronPhase phase : CauldronPhase.values()) {
            for (boolean filled : new boolean[]{false, true}) {
                for (boolean loaded : new boolean[]{false, true}) {
                    assertTrue(CauldronVisual.of(phase, filled, loaded) != null,
                            phase + "/" + filled + "/" + loaded);
                }
            }
        }
    }

    @Test
    void idleIsTheOnlyPhaseThatSplits() {
        // Three visuals out of IDLE and one each from the rest. If a future phase needs two looks,
        // this is the assertion that should be revisited deliberately rather than quietly broken.
        assertEquals(3, java.util.Arrays.stream(new CauldronVisual[]{
                        CauldronVisual.of(CauldronPhase.IDLE, false, false),
                        CauldronVisual.of(CauldronPhase.IDLE, true, false),
                        CauldronVisual.of(CauldronPhase.IDLE, true, true)})
                .distinct().count());
    }

    @Test
    void onlyAnEmptyPotHasNoLiquid() {
        for (CauldronVisual visual : CauldronVisual.values()) {
            assertEquals(visual != CauldronVisual.EMPTY, visual.hasLiquid(), visual.toString());
        }
    }

    @Test
    void serialisedNamesAreUniqueAndLowercase() {
        // They become blockstate values, so a collision would make two conditions indistinguishable
        // in the blockstate JSON and to any resource pack keying off it.
        java.util.Set<String> names = new java.util.HashSet<>();
        for (CauldronVisual visual : CauldronVisual.values()) {
            String name = visual.getSerializedName();
            assertEquals(name.toLowerCase(java.util.Locale.ROOT), name, "must be lowercase");
            assertTrue(names.add(name), "duplicate blockstate value: " + name);
        }
        assertEquals(CauldronVisual.values().length, names.size());
    }

    @Test
    void thePropertyIsNamedForWhatItIsNotForItsType() {
        assertEquals("visual", CauldronVisual.PROPERTY.getName());
    }

    @Test
    void theTintableFaceExistsInTheGeneratedModel() throws Exception {
        // The blockstate is only half of it: without tintindex 0 on the pot's opening, the colour
        // handler has nothing to paint and every state renders identically.
        String json = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/generated/resources/assets/wizards_and_beasts/models/block/pewter_cauldron.json"));
        assertTrue(json.contains("\"tintindex\": 0"),
                "the cauldron model must expose a tintable face, or the visual states are invisible");
    }

    @Test
    void allThreeMetalsShareTheTintableModel() throws Exception {
        for (String metal : new String[]{"pewter_cauldron", "brass_cauldron", "wizarding_copper_cauldron"}) {
            String json = java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/generated/resources/assets/wizards_and_beasts/models/block/" + metal + ".json"));
            assertTrue(json.contains("\"tintindex\": 0"), metal + " is missing its tintable face");
        }
    }
}
