package at.koopro.wizardsandbeasts.settlement.shop;

import at.koopro.wizardsandbeasts.settlement.SettlementData;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShelfRulesTest {

    private static final String SHOP = "wizards_and_beasts:apothecary";
    private static final int DAY = 24000;

    @Test
    void aShelfNobodyBoughtFromIsFull() {
        assertEquals(8, ShelfRules.left(null, 8, 4, 123));
    }

    @Test
    void buyingTakesFromTheShelf() {
        SettlementData.Shelf shelf = ShelfRules.after(null, 8, 4, 0, 3);
        assertEquals(5, ShelfRules.left(shelf, 8, 4, 0));
    }

    @Test
    void anEmptyShelfFillsOverItsRestockDaysAndNoFurther() {
        SettlementData.Shelf empty = ShelfRules.after(null, 8, 4, 0, 8);
        assertEquals(0, ShelfRules.left(empty, 8, 4, 0));
        assertEquals(4, ShelfRules.left(empty, 8, 4, 2 * DAY));
        assertEquals(8, ShelfRules.left(empty, 8, 4, 4 * DAY));
        assertEquals(8, ShelfRules.left(empty, 8, 4, 400 * DAY));
    }

    @Test
    void readingAShelfNeverChangesIt() {
        SettlementData.Shelf shelf = ShelfRules.after(null, 8, 4, 0, 8);
        for (int i = 0; i < 100; i++) {
            assertEquals(0, ShelfRules.left(shelf, 8, 4, 100));
        }
    }

    @Test
    void aUnitPartWayBackKeepsItsProgressThroughASale() {
        long unit = ShelfRules.ticksPerUnit(8, 4);
        SettlementData.Shelf empty = ShelfRules.after(null, 8, 4, 0, 8);
        // One unit and a half back, then that unit is bought: the half still counts.
        long now = unit + unit / 2;
        SettlementData.Shelf after = ShelfRules.after(empty, 8, 4, now, 1);
        assertEquals(0, ShelfRules.left(after, 8, 4, now));
        assertEquals(1, ShelfRules.left(after, 8, 4, now + unit / 2));
    }

    @Test
    void aSaleFromAFullShelfStartsTheClockThen() {
        SettlementData.Shelf after = ShelfRules.after(null, 8, 4, 500 * DAY, 1);
        assertEquals(7, ShelfRules.left(after, 8, 4, 500 * DAY));
    }

    @Test
    void aShelfCannotGoBelowEmpty() {
        assertEquals(0, ShelfRules.after(null, 2, 4, 0, 9).left());
    }

    @Test
    void theSameSiteAlwaysCarriesTheSameGoods() {
        for (long seed = 0; seed < 50; seed++) {
            assertEquals(ShelfRules.carries(seed, SHOP, "bezoar", 0.5f), ShelfRules.carries(seed, SHOP, "bezoar", 0.5f));
            assertEquals(ShelfRules.capacity(seed, SHOP, "bezoar", 8), ShelfRules.capacity(seed, SHOP, "bezoar", 8));
        }
    }

    @Test
    void sitesDifferInWhatTheyCarryAndHowMuch() {
        Set<Boolean> carried = new HashSet<>();
        Set<Integer> amounts = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            carried.add(ShelfRules.carries(seed * 7919, SHOP, "bezoar", 0.5f));
            amounts.add(ShelfRules.capacity(seed * 7919, SHOP, "bezoar", 16));
        }
        assertEquals(2, carried.size(), "some sites stock it and some do not");
        assertTrue(amounts.size() > 3, "full shelves vary between sites: " + amounts);
    }

    @Test
    void anEssentialIsCarriedEverywhereAndARarityByFew() {
        int rare = 0;
        for (long seed = 0; seed < 400; seed++) {
            assertTrue(ShelfRules.carries(seed * 31, SHOP, "dittany", 1.0f));
            rare += ShelfRules.carries(seed * 31, SHOP, "erumpent_horn", 0.25f) ? 1 : 0;
        }
        assertTrue(rare > 50 && rare < 150, "about a quarter of sites: " + rare);
    }

    @Test
    void noGoodIsCarriedAtChanceZero() {
        assertFalse(ShelfRules.carries(42, SHOP, "anything", 0.0f));
    }

    @Test
    void aFullShelfIsNeverEmptyHoweverSmallItsStock() {
        for (long seed = 0; seed < 100; seed++) {
            assertTrue(ShelfRules.capacity(seed, SHOP, "felix", 1) >= 1);
        }
    }
}
