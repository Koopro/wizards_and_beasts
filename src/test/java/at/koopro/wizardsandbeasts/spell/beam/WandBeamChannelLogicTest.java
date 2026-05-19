package at.koopro.wizardsandbeasts.spell.beam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WandBeamChannelLogicTest {

    @Test
    void sessionSyncSpell_keepsStateWhenSpellUnchanged() {
        WandBeamSession session = new WandBeamSession();
        session.syncSpell("crucio");
        session.beamTicks = 9;
        session.avadaConsumed = true;

        session.syncSpell("crucio");

        assertEquals(9, session.beamTicks);
        assertTrue(session.avadaConsumed);
        assertEquals("crucio", session.spellId);
    }

    @Test
    void sessionSyncSpell_resetsStateWhenSpellChanges() {
        WandBeamSession session = new WandBeamSession();
        session.syncSpell("crucio");
        session.beamTicks = 15;
        session.avadaConsumed = true;
        session.lastCrucioTarget = java.util.UUID.randomUUID();
        session.cachedTarget = java.util.UUID.randomUUID();

        session.syncSpell("avada_kedavra");

        assertEquals("avada_kedavra", session.spellId);
        assertEquals(0, session.beamTicks);
        assertFalse(session.avadaConsumed);
        assertNull(session.lastCrucioTarget);
        assertNull(session.cachedTarget);
    }
}
