package at.koopro.wizardsandbeasts.network.admin;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A token bucket per sender in front of every Control Center request.
 *
 * <p>Requests were bounded (packet size, history ring) but not throttled, so any client — an administrator's or
 * not — could send them as fast as it liked and make the server build snapshots, search players and render facets
 * in a loop. Authority is checked after this, in the services; this only caps the rate, for everyone.
 *
 * <p>Sized for the real screen: opening the Control Center sends a snapshot request and, when global search opens,
 * one list request per section; a slider dragged with the keyboard sends a change per step. {@link #BURST} covers a
 * cold open with search; {@link #PER_SECOND} is far above anything a person does with a mouse. A request over the
 * limit is dropped (no reply) and logged at most once per {@link #WARN_EVERY_MS} per sender.
 *
 * <p>Thread-safe: payload handlers run on the network thread.
 */
@NullMarked
@net.neoforged.fml.common.EventBusSubscriber(modid = at.koopro.wizardsandbeasts.WizardsAndBeastsMod.MODID)
public final class AdminRequestLimiter {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final double BURST = 80.0;
    static final double PER_SECOND = 30.0;
    private static final long WARN_EVERY_MS = 5_000L;

    private static final AdminRequestLimiter SHARED = new AdminRequestLimiter(System::currentTimeMillis);

    /** Requests admitted / dropped since the server started — real counts for Diagnostics → Network. */
    private static final java.util.concurrent.atomic.AtomicLong ADMITTED = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong DROPPED = new java.util.concurrent.atomic.AtomicLong();

    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    AdminRequestLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    private static final class Bucket {
        double tokens = BURST;
        long refilledAt;
        long warnedAt;

        Bucket(long now) {
            refilledAt = now;
            warnedAt = now - WARN_EVERY_MS;
        }
    }

    /** Whether a request from {@code sender} is admitted; requests with no player (none in practice) always are. */
    public static boolean admit(@Nullable Player sender) {
        if (sender == null) {
            return true;
        }
        boolean admitted = SHARED.tryAcquire(sender.getUUID());
        (admitted ? ADMITTED : DROPPED).incrementAndGet();
        if (!admitted && SHARED.shouldWarn(sender.getUUID())) {
            LOGGER.warn("[Admin] Dropping Control Center requests from {} ({}): over {} per second",
                    sender.getName().getString(), sender.getUUID(), (int) PER_SECOND);
        }
        return admitted;
    }

    public static long admittedCount() {
        return ADMITTED.get();
    }

    public static long droppedCount() {
        return DROPPED.get();
    }

    /** Forgets a sender, so a reconnect starts with a full bucket. */
    public static void forget(UUID sender) {
        SHARED.buckets.remove(sender);
    }

    @net.neoforged.bus.api.SubscribeEvent
    static void onLogout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    boolean tryAcquire(UUID sender) {
        long now = clock.getAsLong();
        Bucket bucket = buckets.computeIfAbsent(sender, id -> new Bucket(now));
        synchronized (bucket) {
            double elapsed = Math.max(0L, now - bucket.refilledAt) / 1000.0;
            bucket.tokens = Math.min(BURST, bucket.tokens + elapsed * PER_SECOND);
            bucket.refilledAt = now;
            if (bucket.tokens < 1.0) {
                return false;
            }
            bucket.tokens -= 1.0;
            return true;
        }
    }

    boolean shouldWarn(UUID sender) {
        Bucket bucket = buckets.get(sender);
        if (bucket == null) {
            return false;
        }
        long now = clock.getAsLong();
        synchronized (bucket) {
            if (now - bucket.warnedAt < WARN_EVERY_MS) {
                return false;
            }
            bucket.warnedAt = now;
            return true;
        }
    }
}
