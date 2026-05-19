package at.koopro.wizardsandbeasts.currency.vault;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CurrencyHelperLoreTest {

    @Test
    void canonicalRatios_remainStable() {
        assertEquals(29, CurrencyHelper.KNUTS_PER_SICKLE);
        assertEquals(17, CurrencyHelper.SICKLES_PER_GALLEON);
        assertEquals(493, CurrencyHelper.KNUTS_PER_GALLEON);
    }

    @Test
    void canonicalCoinIdClassification_excludesDragot() {
        Identifier dragot = Identifier.fromNamespaceAndPath("wizards_and_beasts", "dragot");
        assertTrue(CurrencyHelper.isCanonicalCoinId(CurrencyHelper.KNUT_ID));
        assertTrue(CurrencyHelper.isCanonicalCoinId(CurrencyHelper.SICKLE_ID));
        assertTrue(CurrencyHelper.isCanonicalCoinId(CurrencyHelper.GALLEON_ID));
        assertFalse(CurrencyHelper.isCanonicalCoinId(dragot));
    }

    @Test
    void selectHighestCanonicalCoinSlot_prefersHighestValueAndStableTieOrder() {
        List<Identifier> slots = List.of(
                Identifier.fromNamespaceAndPath("minecraft", "air"),
                CurrencyHelper.KNUT_ID,
                CurrencyHelper.SICKLE_ID,
                CurrencyHelper.GALLEON_ID,
                CurrencyHelper.SICKLE_ID
        );
        assertEquals(3, CurrencyHelper.selectHighestCanonicalCoinSlot(slots));

        List<Identifier> tieSlots = List.of(
                CurrencyHelper.GALLEON_ID,
                CurrencyHelper.GALLEON_ID,
                CurrencyHelper.KNUT_ID
        );
        assertEquals(0, CurrencyHelper.selectHighestCanonicalCoinSlot(tieSlots));
    }
}
