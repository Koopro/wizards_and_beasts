package at.koopro.wizardsandbeasts.apparition;

import net.minecraft.world.entity.player.Inventory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the inventory geometry a splinch reaches into.
 *
 * <p>{@code Inventory.getContainerSize()} is <b>not</b> the pack. In 1.21.11 it is
 * {@code items.size() + EQUIPMENT_SLOT_MAPPING.size()} — 36 real slots plus the four armour pieces, the
 * off-hand, body and saddle — and both the residue drop and the encumbrance check used to iterate it. The
 * consequences were quiet and bad: a splinch could tear the robes off a wizard's back and the wand out of
 * their off-hand, and wearing a full set of armour measurably pushed a wizard toward being "encumbered" by
 * the pack they had not filled.
 *
 * <p>There is no way to unit-test the drop itself without a live player, so what is locked here is the fact
 * the code now depends on: that the two numbers are different, and that the pack is the smaller one. If a
 * future version folds equipment into {@code items}, this fails and points at the code that assumed
 * otherwise.
 */
class SplinchResidueScopeTest {

    @Test
    void thePackIsThirtySixSlots() {
        assertEquals(36, Inventory.INVENTORY_SIZE);
    }

    @Test
    void theContainerIsBiggerThanThePackBecauseItCountsWornGear() {
        assertTrue(Inventory.EQUIPMENT_SLOT_MAPPING.size() > 0,
                "equipment slots have merged into the pack — the residue drop and the encumbrance check "
                        + "both assume Inventory.INVENTORY_SIZE excludes worn and held gear");
        assertTrue(Inventory.SLOT_OFFHAND >= Inventory.INVENTORY_SIZE,
                "the off-hand has moved inside the pack range, which would put a held wand back in reach "
                        + "of a splinch");
    }
}
