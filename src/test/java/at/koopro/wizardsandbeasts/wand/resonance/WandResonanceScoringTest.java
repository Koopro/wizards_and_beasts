package at.koopro.wizardsandbeasts.wand.resonance;

import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.wand.stat.WandFlexibility;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two factors that made bonding unreachable, pinned as arithmetic.
 *
 * <p>Core temperament compared {@code raw_power} (an absolute 1.15–1.8 scale) against an ideal
 * normalised to 0–1, so the 0.3 core weight contributed ≈0.016 at best; wood affinity compared wood
 * personality words against heritage ids and tags, which share no vocabulary, so it returned a hard 0
 * for every player who had chosen a heritage. Together they put 0.65 out of reach for everyone.
 */
class WandResonanceScoringTest {

    // Shipped core powers (data/wizards_and_beasts/wizards_and_beasts/wand_cores).
    private static final float UNICORN_HAIR = 1.15f;
    private static final float PHOENIX_FEATHER = 1.4f;
    private static final float DRAGON_HEARTSTRING = 1.8f;

    @Test
    void aNoviceResonatesWithTheSteadiestCore() {
        float unicorn = WandResonanceSystem.coreTemperamentScore(UNICORN_HAIR, 0);
        float phoenix = WandResonanceSystem.coreTemperamentScore(PHOENIX_FEATHER, 0);
        float dragon = WandResonanceSystem.coreTemperamentScore(DRAGON_HEARTSTRING, 0);
        assertTrue(unicorn > phoenix, "unicorn " + unicorn + " should beat phoenix " + phoenix);
        assertTrue(phoenix > dragon, "phoenix " + phoenix + " should beat dragon " + dragon);
    }

    @Test
    void anAccomplishedWizardResonatesWithTheMostVolatileCore() {
        float unicorn = WandResonanceSystem.coreTemperamentScore(UNICORN_HAIR, 40);
        float dragon = WandResonanceSystem.coreTemperamentScore(DRAGON_HEARTSTRING, 40);
        assertTrue(dragon > unicorn, "dragon " + dragon + " should beat unicorn " + unicorn + " at level 40");
    }

    @Test
    void someCoreScoresHighForEveryLevel() {
        // The whole 0.3 core weight is dead if the best available core still scores near zero.
        for (int level = 0; level <= 60; level += 5) {
            float best = Math.max(WandResonanceSystem.coreTemperamentScore(UNICORN_HAIR, level),
                    Math.max(WandResonanceSystem.coreTemperamentScore(PHOENIX_FEATHER, level),
                            WandResonanceSystem.coreTemperamentScore(DRAGON_HEARTSTRING, level)));
            assertTrue(best > 0.6f, "best core score at level " + level + " was only " + best);
        }
    }

    @Test
    void coreScoreStaysWithinTheUnitRange() {
        for (float power : new float[]{0.0f, 1.0f, UNICORN_HAIR, DRAGON_HEARTSTRING, 5.0f}) {
            for (int level : new int[]{0, 15, 30, 100}) {
                float score = WandResonanceSystem.coreTemperamentScore(power, level);
                assertTrue(score >= 0.0f && score <= 1.0f, "score " + score + " out of range");
            }
        }
    }

    @Test
    void woodAffinityIsGradedAndNeverZero() {
        Set<String> traits = Set.of("gentle", "guardian", "curious");
        assertEquals(1.0f, WandResonanceSystem.woodAffinityScore(traits, List.of("gentle", "guardian", "empathetic")));
        assertEquals(0.8f, WandResonanceSystem.woodAffinityScore(traits, List.of("visionary", "curious", "seeker")));
        assertEquals(WandResonanceSystem.UNFAVOURED_WOOD_AFFINITY,
                WandResonanceSystem.woodAffinityScore(traits, List.of("conqueror", "destined", "exceptional")),
                "an unfavoured wood must not score 0 — that alone puts the match threshold out of reach");
    }

    @Test
    void everyHeritageVariantSpeaksTheWoodVocabulary() {
        for (HeritageVariant variant : HeritageVariant.values()) {
            assertTrue(WizardPersonality.of(variant).size() >= 3,
                    variant + " has no personality traits, so every wood scores it as unfavoured");
        }
    }

    /** The whole inequality, composed exactly as {@code computeResonance} does. */
    private static float total(float woodAffinity, float corePower, float lengthInches,
                               WandFlexibility flex, int level) {
        WandResonanceConfig cfg = WandResonanceConfig.DEFAULT;
        return cfg.woodWeight() * woodAffinity
                + cfg.coreWeight() * WandResonanceSystem.coreTemperamentScore(corePower, level)
                + cfg.lengthWeight() * WandResonanceSystem.lengthAffinityScore(lengthInches, VANILLA_ENTITY_REACH)
                + cfg.flexibilityWeight() * WandResonanceSystem.flexibilityScore(flex, level, false);
    }

    private static final double VANILLA_ENTITY_REACH = 3.0;

    @Test
    void aFirstDayWizardCanBondTheWandThatFavoursThem() {
        // Level 0, a wood that names one of their traits, the starter core, an 11" trial wand.
        float score = total(0.8f, UNICORN_HAIR, 11.0f, WandFlexibility.PLIANT, 0);
        assertTrue(score >= WandResonanceConfig.DEFAULT.matchThreshold(),
                "a favoured starter wand scored " + score + ", under the "
                        + WandResonanceConfig.DEFAULT.matchThreshold() + " match threshold — Ollivander's "
                        + "would send a new wizard away empty-handed and nothing in the mod could be cast");
    }

    @Test
    void anUnsuitedWandStillFallsShort() {
        // An unfavoured wood, the wrong core for the wizard's standing, an awkward length.
        float score = total(WandResonanceSystem.UNFAVOURED_WOOD_AFFINITY, DRAGON_HEARTSTRING, 14.0f,
                WandFlexibility.SPRINGY, 0);
        assertTrue(score < WandResonanceConfig.DEFAULT.matchThreshold(),
                "the wand is supposed to choose the wizard, but this one scored " + score);
    }

    @Test
    void aSeasonedWizardCanBondTheVolatileCore() {
        float score = total(0.8f, DRAGON_HEARTSTRING, 13.0f, WandFlexibility.SPRINGY, 40);
        assertTrue(score >= WandResonanceConfig.DEFAULT.matchThreshold(), "scored " + score);
    }

    @Test
    void variantIdsAndCapabilityTagsStillMatchForDatapacks() {
        Set<String> traits = WandResonanceSystem.traitsOf(HeritageVariant.PURE_BLOOD);
        assertTrue(traits.contains("pure_blood"), "variant id must remain matchable");
        assertTrue(traits.contains("enhanced_bond"), "capability tags must remain matchable");
    }
}
