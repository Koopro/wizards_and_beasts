package at.koopro.wizardsandbeasts.wand.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A wand's fittings follow its wood's rarity; the old ornamented constructions are always gilt. */
class WandMetalTest {

    @Test
    void theMetalFollowsTheWoodsRarity() {
        assertEquals(WandMetal.PEWTER, WandMetal.of("common", -1));
        assertEquals(WandMetal.PEWTER, WandMetal.of("uncommon", -1));
        assertEquals(WandMetal.SILVER, WandMetal.of("rare", -1));
        assertEquals(WandMetal.GOLD, WandMetal.of("legendary", -1));
        assertEquals(WandMetal.PEWTER, WandMetal.of(null, -1));
    }

    @Test
    void aRareConstructionIsGiltWhateverTheWood() {
        assertEquals(WandMetal.GOLD, WandMetal.of("common", 0));
        assertEquals(WandMetal.GOLD, WandMetal.of(null, 1));
    }
}
