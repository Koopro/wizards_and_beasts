package at.koopro.wizardsandbeasts.apparition.sidealong;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An accepted side-along agreement is consent for the next jump, not for the rest of the session. It used to
 * be stored with no expiry, so a partner who agreed and wandered off could be taken along an hour later.
 */
class SideAlongAgreementTest {

    @Test
    void consentLapsesAfterTheWindow() {
        var agreement = new SideAlongService.Agreement(UUID.randomUUID(), 1_000 + SideAlongService.AGREEMENT_TICKS);
        assertTrue(agreement.liveAt(1_000));
        assertTrue(agreement.liveAt(1_000 + SideAlongService.AGREEMENT_TICKS));
        assertFalse(agreement.liveAt(1_000 + SideAlongService.AGREEMENT_TICKS + 1));
    }

    @Test
    void theWindowIsShortComparedWithASession() {
        assertTrue(SideAlongService.AGREEMENT_TICKS <= 20 * 60, "consent should be for 'now', not minutes");
    }
}
