package at.koopro.wizardsandbeasts.brew;

import com.google.gson.JsonParser;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * When a brew goes wrong, and how wrong.
 *
 * <p>The roll itself needs a level, so what is pinned here is the arithmetic that decides it — the
 * one place where every contribution is folded together — plus the catalyst window and the shape of
 * the ruined brew. Failure chance is the kind of number that gets tuned by feel and then quietly
 * stops meaning what it did, which is exactly what a test is for.
 */
class BrewFailureTest {

    // ── the arithmetic ─────────────────────────────────────────────────────────────────────

    @Test
    void anEasyRecipeWithNothingWrongCannotFail() {
        // The compatibility guarantee. Every recipe that existed before difficulty declared no
        // failureChance, and must stay exactly as reliable as it was.
        assertEquals(0f, BrewFailure.chance(0f, 0f, 0));
        assertEquals(0f, BrewFailure.chance(0f, 0f, 3));
    }

    @Test
    void aRiskyRecipeFailsAtItsDeclaredRateForANovice() {
        assertEquals(0.35f, BrewFailure.chance(0.35f, 0f, 0), 1e-6);
    }

    @Test
    void penaltiesAddToTheRecipesOwnDifficulty() {
        assertEquals(0.60f, BrewFailure.chance(0.35f, 0.25f, 0), 1e-6);
    }

    @Test
    void skillReducesButNeverToCertainty() {
        float novice = BrewFailure.chance(0.40f, 0f, 0);
        float expert = BrewFailure.chance(0.40f, 0f, BrewFailure.MAX_LEVEL_BONUS);

        assertTrue(expert < novice, "skill must help");
        assertTrue(expert > 0f, "skill must not buy certainty on a risky recipe");
        assertEquals(BrewFailure.FLOOR_WHEN_RISKY, BrewFailure.chance(0.02f, 0f, 99), 1e-6);
    }

    @Test
    void skillBeyondTheCapStopsHelping() {
        assertEquals(BrewFailure.chance(0.40f, 0f, BrewFailure.MAX_LEVEL_BONUS),
                BrewFailure.chance(0.40f, 0f, 500), 1e-6);
    }

    @Test
    void aNegativeSkillLevelIsTreatedAsNone() {
        // Corrupt skill data must not make a brew MORE likely to succeed than an unskilled one.
        assertEquals(BrewFailure.chance(0.40f, 0f, 0), BrewFailure.chance(0.40f, 0f, -5), 1e-6);
    }

    @Test
    void chanceIsClampedToOne() {
        assertEquals(1f, BrewFailure.chance(0.9f, 0.9f, 0), 1e-6);
    }

    @Test
    void negativeInputsCannotDriveTheChanceBelowZero() {
        assertEquals(0f, BrewFailure.chance(-1f, -1f, 0));
    }

    // ── the catalyst window ────────────────────────────────────────────────────────────────

    private static BrewingRecipe.Catalyst window(float start, float end) {
        return new BrewingRecipe.Catalyst(Items.SUGAR, start, end, 0.4f);
    }

    @Test
    void theWindowIsInclusiveAtBothEnds() {
        BrewingRecipe.Catalyst c = window(0.4f, 0.7f);
        assertTrue(c.acceptsAt(0.4f), "the moment it opens counts");
        assertTrue(c.acceptsAt(0.7f), "the moment it closes counts");
        assertTrue(c.acceptsAt(0.55f));
    }

    @Test
    void outsideTheWindowIsRejected() {
        BrewingRecipe.Catalyst c = window(0.4f, 0.7f);
        assertFalse(c.acceptsAt(0.39f), "too early");
        assertFalse(c.acceptsAt(0.71f), "too late");
        assertFalse(c.acceptsAt(0f));
        assertFalse(c.acceptsAt(1f));
    }

    @Test
    void aBackwardsWindowIsRefusedAtConstruction() {
        // A datapack typo. Better to reject it where it is written than to ship a catalyst that can
        // never be satisfied and a brew nobody can work out how to make.
        assertThrows(IllegalArgumentException.class, () -> window(0.7f, 0.4f));
    }

    @Test
    void theWindowIsAFractionSoRetuningTheBrewTimeDoesNotMoveIt() {
        // Expressed as progress rather than ticks: "halfway through" stays halfway through when
        // somebody doubles heatTimeTicks.
        BrewingRecipe.Catalyst c = window(0.45f, 0.65f);
        assertTrue(c.windowStart() < 1f && c.windowEnd() <= 1f,
                "a window expressed in ticks would be > 1 here and would silently never open");
    }

    // ── recipe defaults ────────────────────────────────────────────────────────────────────

    @Test
    void aRecipeWithoutDifficultyDeclaresNone() {
        BrewingRecipe plain = new BrewingRecipe("test:plain",
                java.util.List.of(new BrewingRecipe.Ingredient(Items.SUGAR, 1)),
                CauldronTier.PEWTER, 200, "test:brew");

        assertEquals(0f, plain.failureChance());
        assertEquals(Optional.empty(), plain.catalyst());
    }

    // ── the shipped data ───────────────────────────────────────────────────────────────────

    @Test
    void theRuinedBrewExistsAndIsUnpleasant() throws Exception {
        String json = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/data/wizards_and_beasts/brews/ruined_potion.json"));
        var doc = JsonParser.parseString(json).getAsJsonObject();

        assertTrue(doc.has("components"));
        assertTrue(json.contains("minecraft:nausea"), "a ruined brew should make you ill");
        assertTrue(json.contains("minecraft:hunger"));
    }

    @Test
    void everyBrewColourIsASignedInt() throws Exception {
        // The repo's convention, and the safe one: an unsigned colour above 2^31 only parses through
        // Codec.INT because Gson truncates it on the way in.
        for (var path : java.nio.file.Files.newDirectoryStream(java.nio.file.Path.of(
                "src/main/resources/data/wizards_and_beasts/brews"))) {
            var doc = JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
            long colour = doc.get("color").getAsLong();
            assertTrue(colour >= Integer.MIN_VALUE && colour <= Integer.MAX_VALUE,
                    path.getFileName() + " has a colour outside int range: " + colour);
        }
    }

    @Test
    void theHardBrewsDeclareTheirDifficulty() throws Exception {
        // Felix is "disastrous if got wrong" and the Draught of Living Death is the exam question.
        // If either ever becomes a guaranteed brew, that is a balance decision worth noticing.
        for (String recipe : new String[]{"felix_felicis", "draught_of_living_death"}) {
            var doc = JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src/main/resources/data/wizards_and_beasts/brewing_recipes/" + recipe + ".json")))
                    .getAsJsonObject();
            assertTrue(doc.has("failureChance"), recipe + " should be a difficult brew");
            assertTrue(doc.get("failureChance").getAsFloat() > 0f, recipe);
        }
    }
}
