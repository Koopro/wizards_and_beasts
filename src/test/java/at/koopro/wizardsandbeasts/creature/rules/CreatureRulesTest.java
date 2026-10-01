package at.koopro.wizardsandbeasts.creature.rules;

import at.koopro.wizardsandbeasts.creature.variant.CreatureVariant;
import at.koopro.wizardsandbeasts.creature.variant.CreatureVariants;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The creature rules and the one variant roll every variant-bearing creature uses. */
class CreatureRulesTest {

    @AfterEach
    void reset() {
        CreatureRules.publish(Map.of());
    }

    private enum Coat implements CreatureVariant {
        A(6), B(3), C(1);

        private final int weight;

        Coat(int weight) {
            this.weight = weight;
        }

        @Override
        public String variantTexture() {
            return null;
        }

        @Override
        public int authoredWeight() {
            return weight;
        }
    }

    @Test
    void withoutRulesEverythingIsAsShipped() {
        assertTrue(CreatureRules.naturalSpawn("anything"));
        assertTrue(CreatureRules.variantEnabled("test", Coat.B));
        assertEquals(3, CreatureRules.variantWeight("test", Coat.B));
    }

    @Test
    void theRollWithoutRulesKeepsAuthoredProportions() {
        Map<Coat, Integer> counts = roll(20_000);
        assertEquals(0.6, counts.get(Coat.A) / 20_000.0, 0.02);
        assertEquals(0.3, counts.get(Coat.B) / 20_000.0, 0.02);
        assertEquals(0.1, counts.get(Coat.C) / 20_000.0, 0.02);
    }

    @Test
    void aDisabledVariantIsNeverRolledAndWeightsApply() {
        CreatureRules.publish(Map.of("test", CreatureRule.NONE
                .withVariantEnabled("a", Optional.of(false))
                .withVariantWeight("c", Optional.of(3))));
        Map<Coat, Integer> counts = roll(10_000);
        assertEquals(0, counts.get(Coat.A));
        assertEquals(0.5, counts.get(Coat.B) / 10_000.0, 0.03);
        assertEquals(0.5, counts.get(Coat.C) / 10_000.0, 0.03);
    }

    @Test
    void allZeroWeightsFallBackToTheFirstRatherThanFailing() {
        Coat picked = CreatureVariants.rollWeighted(Coat.values(), c -> 0, RandomSource.create(1));
        assertEquals(Coat.A, picked);
    }

    @Test
    void theNaturalSpawnRuleIsPerCreature() {
        CreatureRules.publish(Map.of("troll", CreatureRule.NONE.withNaturalSpawn(Optional.of(false))));
        assertFalse(CreatureRules.naturalSpawn("troll"));
        assertTrue(CreatureRules.naturalSpawn("unicorn"));
    }

    @Test
    void aRuleSurvivesTheWorldFileAndEmptiesCleanly() {
        CreatureRule rule = CreatureRule.NONE.withNaturalSpawn(Optional.of(false))
                .withVariantEnabled("bronze", Optional.of(false)).withVariantWeight("roan", Optional.of(9));
        JsonElement json = CreatureRule.CODEC.encodeStart(JsonOps.INSTANCE, rule).getOrThrow();
        assertEquals(rule, CreatureRule.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        CreatureRule cleared = rule.withNaturalSpawn(Optional.empty()).withVariantEnabled("bronze", Optional.empty())
                .withVariantWeight("roan", Optional.empty());
        assertTrue(cleared.isEmpty());
        assertNotEquals(rule, cleared);
    }

    @Test
    void theShippedVariantSetsAreTheEntitiesOwn() {
        List<CreatureVariant> niffler = CreatureVariants.of("niffler");
        assertEquals(List.of("classic", "dark_brown", "grey", "pale"), niffler.stream().map(CreatureVariant::variantId).toList());
        assertEquals(List.of(70, 12, 12, 6), niffler.stream().map(CreatureVariant::authoredWeight).toList());
        assertEquals(5, CreatureVariants.of("hippogriff").size());
        assertEquals(3, CreatureVariants.of("kelpie").size());
        assertTrue(CreatureVariants.of("unicorn").isEmpty());
        assertEquals("bronze", CreatureVariants.byId("hippogriff", "bronze").variantId());
    }

    private static Map<Coat, Integer> roll(int times) {
        Map<Coat, Integer> counts = new EnumMap<>(Coat.class);
        for (Coat coat : Coat.values()) {
            counts.put(coat, 0);
        }
        RandomSource random = RandomSource.create(42);
        for (int i = 0; i < times; i++) {
            counts.merge(CreatureVariants.roll("test", Coat.values(), random), 1, Integer::sum);
        }
        return counts;
    }
}
