package at.koopro.wizardsandbeasts.data;

import at.koopro.wizardsandbeasts.spell.data.PlayerSpellData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that PlayerSpellData survives a save -> load -> save round trip
 * without losing fields. Compares CompoundTags rather than relying on
 * PlayerSpellData equality (which is identity-based today).
 */
class PlayerSpellDataRoundTripTest {

    @Test
    void emptyData_roundTripIsStable() {
        PlayerSpellData original = new PlayerSpellData();

        CompoundTag firstSave = original.save();

        PlayerSpellData reloaded = new PlayerSpellData();
        reloaded.load(firstSave);

        CompoundTag secondSave = reloaded.save();

        assertEquals(firstSave, secondSave, "Empty PlayerSpellData should be stable across save/load.");
    }

    @Test
    void populatedData_preservesAllFields() {
        PlayerSpellData original = new PlayerSpellData();
        original.learnSpell("lumos");
        original.learnSpell("nox");
        original.learnSpell("stupefy");
        original.setLoadoutSpell(0, "stupefy");
        original.setLoadoutSpell(1, "lumos");
        original.setLoadoutSpell(2, "nox");
        original.setActiveSlot(2);
        original.setCooldown("stupefy", 12345L);
        original.setCooldown("lumos", 6789L);
        original.incrementCastCount("stupefy");
        original.incrementCastCount("stupefy");
        original.incrementCastCount("lumos");

        CompoundTag firstSave = original.save();

        PlayerSpellData reloaded = new PlayerSpellData();
        reloaded.load(firstSave);

        assertTrue(reloaded.knowsSpell("lumos"));
        assertTrue(reloaded.knowsSpell("nox"));
        assertTrue(reloaded.knowsSpell("stupefy"));
        assertFalse(reloaded.knowsSpell("avada_kedavra"));
        assertEquals("stupefy", reloaded.getLoadoutSpell(0));
        assertEquals("lumos", reloaded.getLoadoutSpell(1));
        assertEquals("nox", reloaded.getLoadoutSpell(2));
        assertEquals(2, reloaded.getActiveSlot());
        assertEquals(12345L, reloaded.getCooldownExpiry("stupefy"));
        assertEquals(6789L, reloaded.getCooldownExpiry("lumos"));
        assertEquals(2, reloaded.getCastCount("stupefy"));
        assertEquals(1, reloaded.getCastCount("lumos"));
        assertEquals(0, reloaded.getCastCount("nox"));

        assertEquals(firstSave, reloaded.save(),
                "Populated PlayerSpellData should be byte-for-byte stable across save/load.");
    }

    @Test
    void forgetSpell_clearsLoadoutSlots() {
        PlayerSpellData data = new PlayerSpellData();
        data.learnSpell("lumos");
        data.setLoadoutSpell(0, "lumos");
        data.setLoadoutSpell(1, "lumos");

        data.forgetSpell("lumos");

        assertFalse(data.knowsSpell("lumos"));
        assertEquals(null, data.getLoadoutSpell(0));
        assertEquals(null, data.getLoadoutSpell(1));
    }

    @Test
    void rejectCounts_clear_survivesRoundTrip() {
        PlayerSpellData data = new PlayerSpellData();
        data.incrementRejectReason("not_holding_wand");
        data.incrementRejectReason("not_holding_wand");
        data.incrementRejectReason("cooldown_active");
        data.learnSpell("lumos");

        CompoundTag firstSave = data.save();
        PlayerSpellData reloaded = new PlayerSpellData();
        reloaded.load(firstSave);
        assertEquals(2, reloaded.getRejectCounts().get("not_holding_wand"));
        assertEquals(1, reloaded.getRejectCounts().get("cooldown_active"));

        reloaded.clearRejectCounts();
        assertTrue(reloaded.getRejectCounts().isEmpty());
        assertTrue(reloaded.knowsSpell("lumos"));

        CompoundTag afterClearSave = reloaded.save();
        PlayerSpellData secondReload = new PlayerSpellData();
        secondReload.load(afterClearSave);
        assertTrue(secondReload.getRejectCounts().isEmpty());
        assertTrue(secondReload.knowsSpell("lumos"));
    }

    @Test
    void copy_isIndependent() {
        PlayerSpellData original = new PlayerSpellData();
        original.learnSpell("lumos");
        original.setLoadoutSpell(0, "lumos");

        PlayerSpellData copy = original.copy();
        copy.learnSpell("nox");
        copy.setLoadoutSpell(1, "nox");

        assertFalse(original.knowsSpell("nox"), "Mutation on copy must not leak into original.");
        assertEquals(null, original.getLoadoutSpell(1));
        assertTrue(copy.knowsSpell("lumos"), "Copy must contain original state.");
    }
}
