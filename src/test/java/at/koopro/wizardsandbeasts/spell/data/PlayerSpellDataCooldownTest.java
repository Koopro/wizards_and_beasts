package at.koopro.wizardsandbeasts.spell.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary coverage for the cooldown state machine the cast path and HUD both read. The strict {@code <}
 * comparison means a spell is off cooldown on the exact expiry tick — the off-by-one that would make the
 * HUD sweep hang a tick or block a re-cast one tick early/late.
 */
class PlayerSpellDataCooldownTest {

    @Test
    void spellIsOffCooldownOnTheExactExpiryTick() {
        PlayerSpellData data = new PlayerSpellData();
        data.setCooldown("lumos", 100L);

        assertTrue(data.isOnCooldown("lumos", 99L), "still on cooldown one tick before expiry");
        assertFalse(data.isOnCooldown("lumos", 100L), "off cooldown on the expiry tick (strict <)");
        assertFalse(data.isOnCooldown("lumos", 101L), "off cooldown after expiry");
    }

    @Test
    void unknownSpellIsNeverOnCooldown() {
        assertFalse(new PlayerSpellData().isOnCooldown("never_cast", 0L));
    }

    @Test
    void setCooldownRoundTripsThroughExpiryGetter() {
        PlayerSpellData data = new PlayerSpellData();
        data.setCooldown("lumos", 123L);
        assertEquals(123L, data.getCooldownExpiry("lumos"));
    }

    @Test
    void globalCooldownSharesTheSameStrictBoundary() {
        PlayerSpellData data = new PlayerSpellData();
        data.setGlobalCooldownEndTick(50L);

        assertTrue(data.isGlobalCooldownActive(49L));
        assertFalse(data.isGlobalCooldownActive(50L), "off on the exact end tick (strict <)");
    }
}
