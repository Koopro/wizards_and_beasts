package at.koopro.wizardsandbeasts.admin.config;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The lost-write guard in {@link ConfigSpecBinding#set}. FML's file watcher can swap in a freshly read config between
 * {@code set} and {@code save}; the write then lands in the discarded object and the old value is saved back. The
 * store here plays that: its first writes are lost, the way a concurrent reload loses them.
 */
class ConfigSpecBindingWriteTest {

    /** A store whose first {@code lost} writes are undone by a "reload" of the old value. */
    private static final class RacingStore {
        final AtomicReference<Double> value = new AtomicReference<>(1.0);
        final AtomicInteger writes = new AtomicInteger();
        final int lost;

        RacingStore(int lost) {
            this.lost = lost;
        }

        void write(double next) {
            if (writes.incrementAndGet() > lost) {
                value.set(next);
            }
        }
    }

    @Test
    void aWriteLostToAReloadIsWrittenAgain() {
        RacingStore store = new RacingStore(1);
        int writes = ConfigSpecBinding.writeUntilHeld(() -> store.write(0.5), store.value::get, 0.5, 4);
        assertEquals(2, writes);
        assertEquals(0.5, store.value.get());
    }

    @Test
    void anUndisturbedWriteHappensOnce() {
        RacingStore store = new RacingStore(0);
        assertEquals(1, ConfigSpecBinding.writeUntilHeld(() -> store.write(0.5), store.value::get, 0.5, 4));
        assertEquals(1, store.writes.get());
    }

    @Test
    void itGivesUpAfterTheLimitAndSaysSo() {
        RacingStore store = new RacingStore(100);
        assertEquals(5, ConfigSpecBinding.writeUntilHeld(() -> store.write(0.5), store.value::get, 0.5, 4));
        assertEquals(4, store.writes.get());
        assertEquals(1.0, store.value.get());
    }
}
