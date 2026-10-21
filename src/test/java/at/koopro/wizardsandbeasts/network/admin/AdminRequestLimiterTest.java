package at.koopro.wizardsandbeasts.network.admin;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-sender token bucket in front of every Control Center request: a burst is admitted, a flood is not, the
 * bucket refills with time, one sender's flood never costs another sender, and the warning is throttled.
 */
class AdminRequestLimiterTest {

    private final AtomicLong now = new AtomicLong(1_000_000L);
    private final AdminRequestLimiter limiter = new AdminRequestLimiter(now::get);

    private int admitted(UUID sender, int attempts) {
        int ok = 0;
        for (int i = 0; i < attempts; i++) {
            ok += limiter.tryAcquire(sender) ? 1 : 0;
        }
        return ok;
    }

    @Test
    void aBurstIsAdmittedAndAFloodIsNot() {
        UUID sender = UUID.randomUUID();
        assertEquals((int) AdminRequestLimiter.BURST, admitted(sender, 500), "exactly the burst gets through at once");
        assertFalse(limiter.tryAcquire(sender));
    }

    @Test
    void theBucketRefillsAtTheSteadyRate() {
        UUID sender = UUID.randomUUID();
        admitted(sender, 500);
        now.addAndGet(1_000L);
        assertEquals((int) AdminRequestLimiter.PER_SECOND, admitted(sender, 500), "one second buys PER_SECOND requests");
        now.addAndGet(60_000L);
        assertEquals((int) AdminRequestLimiter.BURST, admitted(sender, 500), "a long pause refills to the burst, no more");
    }

    @Test
    void sendersHaveTheirOwnBuckets() {
        UUID flooder = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        admitted(flooder, 500);
        assertTrue(limiter.tryAcquire(other), "another administrator is not slowed by someone else's flood");
    }

    @Test
    void theWarningIsThrottled() {
        UUID sender = UUID.randomUUID();
        admitted(sender, 500);
        assertTrue(limiter.shouldWarn(sender));
        assertFalse(limiter.shouldWarn(sender), "a second warning inside the window is suppressed");
        now.addAndGet(6_000L);
        assertTrue(limiter.shouldWarn(sender));
    }

    @Test
    void theRealScreenFitsInTheLimits() {
        // A cold open with global search sends a snapshot plus one list request per browsable section (about a dozen).
        assertTrue(AdminRequestLimiter.BURST >= 40, "a cold open with search must fit in one burst");
        assertTrue(AdminRequestLimiter.PER_SECOND >= 20, "a keyboard-driven slider sends up to 20 changes a second");
    }
}
