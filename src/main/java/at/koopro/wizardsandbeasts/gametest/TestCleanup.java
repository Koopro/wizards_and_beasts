package at.koopro.wizardsandbeasts.gametest;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Undo that happens even when a scenario fails. A failed check stops a GameTest sequence where it stands, so a
 * cleanup written as its last step never runs — and a profile override, rain, or a changed time of day then leaks
 * into whichever scenario runs next, which fails for no reason of its own (found 2026-11-05: a shelter scenario
 * that failed early left the unicorn weather-shy and the world raining for the event-wake and nocturnal scenarios).
 *
 * <p>{@link #guard} wraps an undo: it runs once, either when the scenario calls it or, at the latest, when the
 * deadline passes. One empty-list check per server tick when nothing is pending.
 */
@EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
public final class TestCleanup {

    private record Pending(ServerLevel level, long due, Runnable undo, AtomicBoolean done) {
        void run() {
            if (done.compareAndSet(false, true)) {
                undo.run();
            }
        }
    }

    private static final List<Pending> PENDING = new CopyOnWriteArrayList<>();

    private TestCleanup() {}

    /** {@code undo}, run once: by calling the returned runnable, or by the server {@code ticks} from now. */
    public static Runnable guard(ServerLevel level, int ticks, Runnable undo) {
        Pending pending = new Pending(level, level.getGameTime() + ticks, undo, new AtomicBoolean());
        PENDING.add(pending);
        return () -> {
            pending.run();
            PENDING.remove(pending);
        };
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Pending pending : PENDING) {
            if (pending.level.getGameTime() >= pending.due) {
                pending.run();
                PENDING.remove(pending);
            }
        }
    }
}
