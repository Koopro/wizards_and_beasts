package at.koopro.wizardsandbeasts.wand.visual;

import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A wand's name colour follows its wood; the Elder Wand and the old ornamented wands rank above it. */
class WandRarityTest {

    @Test
    void theWoodSetsTheColour() {
        assertEquals(Rarity.COMMON, WandRarity.of("common", -1, false));
        assertEquals(Rarity.UNCOMMON, WandRarity.of("uncommon", -1, false));
        assertEquals(Rarity.RARE, WandRarity.of("rare", -1, false));
        assertEquals(Rarity.EPIC, WandRarity.of("legendary", -1, false));
        assertEquals(Rarity.COMMON, WandRarity.of(null, -1, false));
    }

    @Test
    void anOrnamentedWandIsAtLeastRareAndTheElderWandLegendary() {
        assertEquals(Rarity.RARE, WandRarity.of("common", 0, false));
        assertEquals(Rarity.EPIC, WandRarity.of("legendary", 0, false));
        assertEquals(Rarity.EPIC, WandRarity.of("rare", -1, true));
    }
}
