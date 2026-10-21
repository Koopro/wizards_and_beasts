package at.koopro.wizardsandbeasts.currency.vault;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The canon coinage and the arithmetic every price, wage and balance goes through: 29 Knuts to the Sickle, 17 Sickles
 * to the Galleon, 493 Knuts to the Galleon — never decimal, never wrapping.
 */
class CurrencyMathTest {

    @Test
    void theCanonRatesHold() {
        assertEquals(29, CurrencyHelper.KNUTS_PER_SICKLE);
        assertEquals(17, CurrencyHelper.SICKLES_PER_GALLEON);
        assertEquals(493, CurrencyHelper.KNUTS_PER_GALLEON);
        assertEquals(CurrencyHelper.KNUTS_PER_SICKLE * CurrencyHelper.SICKLES_PER_GALLEON, CurrencyHelper.KNUTS_PER_GALLEON);
        // Seven Galleons, the canon wand price, and one Galleon, the respec fee.
        assertEquals(3451L, CurrencyHelper.toKnuts(7, 0, 0));
        assertEquals(493L, CurrencyHelper.toKnuts(0, 17, 0));
        assertEquals(29L, CurrencyHelper.toKnuts(0, 0, 29));
    }

    @Test
    void knutsSplitIntoTheFewestCoins() {
        assertArrayEquals(new long[]{0, 0, 0}, CurrencyHelper.fromKnuts(0));
        assertArrayEquals(new long[]{0, 0, 28}, CurrencyHelper.fromKnuts(28));
        assertArrayEquals(new long[]{0, 1, 0}, CurrencyHelper.fromKnuts(29));
        assertArrayEquals(new long[]{0, 16, 28}, CurrencyHelper.fromKnuts(492));
        assertArrayEquals(new long[]{1, 0, 0}, CurrencyHelper.fromKnuts(493));
        assertArrayEquals(new long[]{2, 0, 0}, CurrencyHelper.fromKnuts(986));
        assertArrayEquals(new long[]{1, 0, 7}, CurrencyHelper.fromKnuts(500));
    }

    @Test
    void splittingAndSummingRoundTripForAnyAmount() {
        Random random = new Random(0x5EEDL);
        for (int i = 0; i < 20_000; i++) {
            long knuts = Math.floorMod(random.nextLong(), CurrencyHelper.MAX_VAULT_KNUTS + 1);
            long[] coins = CurrencyHelper.fromKnuts(knuts);
            assertTrue(coins[1] >= 0 && coins[1] < CurrencyHelper.SICKLES_PER_GALLEON, () -> "sickles " + coins[1]);
            assertTrue(coins[2] >= 0 && coins[2] < CurrencyHelper.KNUTS_PER_SICKLE, () -> "knuts " + coins[2]);
            assertEquals(knuts, CurrencyHelper.toKnuts(coins[0], coins[1], coins[2]));
        }
        long[] top = CurrencyHelper.fromKnuts(CurrencyHelper.MAX_VAULT_KNUTS);
        assertEquals(CurrencyHelper.MAX_VAULT_KNUTS, CurrencyHelper.toKnuts(top[0], top[1], top[2]));
    }

    @Test
    void aTotalSaturatesInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE, CurrencyHelper.toKnuts(Long.MAX_VALUE / 100, 0, 0));
        assertEquals(Long.MAX_VALUE, CurrencyHelper.toKnuts(0, Long.MAX_VALUE, 0));
        assertEquals(Long.MAX_VALUE, CurrencyHelper.toKnuts(1, 0, Long.MAX_VALUE));
        assertTrue(CurrencyHelper.MAX_VAULT_KNUTS < Long.MAX_VALUE / CurrencyHelper.KNUTS_PER_GALLEON,
                "the ceiling leaves room for any single total to be computed exactly");
    }

    @Test
    void negativeMoneyIsNeverSplitIntoCoins() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyHelper.fromKnuts(-1));
    }

    @Test
    void formattingNamesEachCoin() {
        assertEquals("0K", CurrencyHelper.formatFromKnuts(0));
        assertEquals("7G", CurrencyHelper.formatFromKnuts(3451));
        assertEquals("1G 7K", CurrencyHelper.formatFromKnuts(500));
        assertEquals("1S 1K", CurrencyHelper.formatFromKnuts(30));
        assertEquals("-2G", CurrencyHelper.formatFromKnuts(-986));
        assertTrue(CurrencyHelper.formatFromKnuts(Long.MIN_VALUE).startsWith("-"));
    }
}
