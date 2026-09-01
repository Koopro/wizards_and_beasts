package at.koopro.wizardsandbeasts.wand;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The wood tint that makes two wands tellable apart in an inventory.
 *
 * <p>Pure arithmetic over an {@code Identifier}, so it is checkable without a client. The property
 * that matters is not any particular colour — it is that <em>different woods get different colours</em>
 * and that an unknown wood still gets a wood-looking one.
 */
class WandAppearanceTest {

    /** Every wood in the shipped Ollivander pool. */
    private static final List<String> CANON_WOODS = List.of(
            "elder", "yew", "holly", "rowan", "ash", "vine", "walnut", "willow", "hawthorn", "blackthorn");

    private static Identifier wood(String path) {
        return Identifier.fromNamespaceAndPath("wizards_and_beasts", path);
    }

    @Test
    void everyShippedWoodGetsItsOwnColour() {
        Set<Integer> seen = new HashSet<>();
        for (String path : CANON_WOODS) {
            int tint = WandAppearance.woodTint(wood(path));
            assertNotEquals(WandAppearance.UNTINTED, tint, path + " must be tinted");
            assertTrue(seen.add(tint), path + " shares a colour with an earlier wood — "
                    + "two wands would look identical, which is the bug this closes");
        }
        assertEquals(CANON_WOODS.size(), seen.size());
    }

    @Test
    void anUnknownWoodStillGetsAStableColour() {
        // A datapack wood must not fall back to "looks like whichever one the renderer picked".
        int first = WandAppearance.woodTint(wood("mahogany"));
        assertNotEquals(WandAppearance.UNTINTED, first);
        for (int i = 0; i < 100; i++) {
            assertEquals(first, WandAppearance.woodTint(wood("mahogany")),
                    "the derived colour must not change between calls");
        }
    }

    @Test
    void derivedColoursStayInTheTimberBand() {
        // The constraint that stops a datapack ever getting a hot magenta wand, which reads as a
        // missing texture rather than as an unfamiliar wood.
        for (String path : List.of("mahogany", "cedar", "bamboo", "driftwood", "zzz", "a", "wandwood_9000")) {
            int argb = WandAppearance.woodTint(wood(path));
            int r = (argb >> 16) & 0xFF;
            int g = (argb >> 8) & 0xFF;
            int b = argb & 0xFF;
            assertEquals(0xFF, (argb >>> 24) & 0xFF, path + " must be fully opaque");
            assertTrue(r >= g && g >= b, path + " must read warm (r >= g >= b), got "
                    + r + "," + g + "," + b);
            assertTrue(r >= 120, path + " must not be near-black, got r=" + r);
        }
    }

    @Test
    void aNullWoodIsUntintedRatherThanBlack() {
        // An unbonded blank wand has no wood component at all; multiplying by 0 would render it black.
        assertEquals(WandAppearance.UNTINTED, WandAppearance.woodTint((Identifier) null));
    }

    @Test
    void namespaceIsIgnoredForTheCanonTen() {
        // A datapack that re-declares "holly" under its own namespace still means holly.
        assertEquals(WandAppearance.woodTint(wood("holly")),
                WandAppearance.woodTint(Identifier.fromNamespaceAndPath("somepack", "holly")));
    }
}
