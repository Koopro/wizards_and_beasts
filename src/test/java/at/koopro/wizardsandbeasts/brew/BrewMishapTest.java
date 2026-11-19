package at.koopro.wizardsandbeasts.brew;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only a pewter pot can melt; every pot can boil over or smoke. */
class BrewMishapTest {

    private static Map<BrewMishap.Kind, Integer> roll(CauldronTier tier) {
        RandomSource random = RandomSource.create(42L);
        Map<BrewMishap.Kind, Integer> seen = new EnumMap<>(BrewMishap.Kind.class);
        for (int i = 0; i < 2000; i++) {
            seen.merge(BrewMishap.pick(tier, random), 1, Integer::sum);
        }
        return seen;
    }

    @Test
    void onlyPewterMelts() {
        for (CauldronTier tier : CauldronTier.values()) {
            Map<BrewMishap.Kind, Integer> seen = roll(tier);
            assertTrue(seen.getOrDefault(BrewMishap.Kind.BOIL_OVER, 0) > 0, tier + " never boiled over");
            assertTrue(seen.getOrDefault(BrewMishap.Kind.SMOKE, 0) > 0, tier + " never smoked");
            if (tier == CauldronTier.PEWTER) {
                assertTrue(seen.getOrDefault(BrewMishap.Kind.MELT, 0) > 300, "pewter almost never melted: " + seen);
            } else {
                assertEquals(0, seen.getOrDefault(BrewMishap.Kind.MELT, 0), tier + " melted: " + seen);
            }
        }
    }
}
