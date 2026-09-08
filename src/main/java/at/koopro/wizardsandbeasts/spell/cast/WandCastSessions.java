package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Server-side ownership of the wand cast session: the one open hold a player may have, and the single
 * release token that hold is worth.
 *
 * <p>The decision itself is {@link CastReleaseGate}, which is free of Minecraft types and carries the
 * reasoning. This class is the adapter — it opens a session when the server sees the hold begin, spends
 * the token when a release is accepted, and drops the session on every path that invalidates the caster.
 *
 * <p>Server-only, and per player. State lives in {@link PlayerScopedState}, so a logout cannot leave an
 * entry behind.
 */
public final class WandCastSessions {

    /**
     * A session cannot outlive the hold that opened it, and vanilla force-releases the wand at its
     * declared use duration. Not a tuning knob — it is that duration.
     */
    public static final long MAX_SESSION_TICKS = WandItem.USE_DURATION_TICKS;

    private static final PlayerScopedState<Session> SESSIONS = PlayerScopedState.create("wand-cast-sessions");

    /**
     * Ids are only ever compared for equality and read by the debug command; they exist so a session
     * can be named in a diagnostic without being confused with the one before it.
     */
    private static final AtomicLong NEXT_ID = new AtomicLong(1L);

    private WandCastSessions() {}

    /** One open wand hold. Mutated only on the server thread. */
    public static final class Session {
        private final long id;
        private final long startGameTick;
        private boolean releaseConsumed;

        private Session(long id, long startGameTick) {
            this.id = id;
            this.startGameTick = startGameTick;
        }

        public long id() {
            return id;
        }

        public long startGameTick() {
            return startGameTick;
        }

        public boolean releaseConsumed() {
            return releaseConsumed;
        }
    }

    /**
     * Opens a session for a hold the server has just seen begin, replacing any previous one.
     *
     * <p>Replacing rather than refusing is deliberate: the previous session's hold is provably over
     * once a new {@code use()} has been read off the same ordered connection, and its release — if it
     * is still in flight — was sent before that {@code use()} and has therefore already been read.
     *
     * @return the new session's id
     */
    public static long begin(ServerPlayer player, long gameTick) {
        Session session = new Session(NEXT_ID.getAndIncrement(), gameTick);
        SESSIONS.put(player.getUUID(), session);
        return session.id();
    }

    /**
     * Offers a release to the player's open session, spending its token when the release is valid.
     *
     * @return {@code null} when the release is accepted (and the token is now spent), otherwise the
     *         gate that refused it
     */
    @Nullable
    public static CastReleaseGate offerRelease(ServerPlayer player, long gameTick) {
        Session session = SESSIONS.get(player.getUUID());
        CastReleaseGate verdict = CastReleaseGate.evaluate(new CastReleaseGate.Inputs(
                player.isAlive(),
                session != null,
                session != null && session.releaseConsumed,
                session == null ? 0L : gameTick - session.startGameTick,
                MAX_SESSION_TICKS));
        if (verdict != null) {
            return verdict;
        }
        // Non-null by construction: a null session yields NO_SESSION above.
        session.releaseConsumed = true;
        return null;
    }

    /**
     * Drops the player's session. Every path that invalidates a caster mid-hold calls this — death,
     * respawn, dimension change, an admin reset — so a release that arrives afterwards meets IDLE
     * rather than a session the caster no longer has any claim to.
     */
    public static void abort(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** The player's open session, for diagnostics. Never mutate what this returns. */
    @Nullable
    public static Session peek(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** Current game tick of the player's level, or {@code 0} off a server level. */
    public static long gameTickOf(ServerPlayer player) {
        return player.level() instanceof ServerLevel level ? level.getGameTime() : 0L;
    }
}
