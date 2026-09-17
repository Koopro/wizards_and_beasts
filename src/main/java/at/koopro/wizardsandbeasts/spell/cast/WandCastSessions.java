package at.koopro.wizardsandbeasts.spell.cast;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import at.koopro.wizardsandbeasts.item.wand.WandItem;
import at.koopro.wizardsandbeasts.registry.ModAttachments;
import at.koopro.wizardsandbeasts.util.PlayerScopedState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
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
        private final @Nullable String spellId;
        private boolean releaseConsumed;
        private boolean vanillaReleaseObserved;
        private boolean clashHold;

        private Session(long id, long startGameTick, @Nullable String spellId) {
            this.id = id;
            this.startGameTick = startGameTick;
            this.spellId = spellId;
        }

        public long id() {
            return id;
        }

        public long startGameTick() {
            return startGameTick;
        }

        /** The active spell when the hold began — the only spell this hold can ever cast. */
        public @Nullable String spellId() {
            return spellId;
        }

        public boolean releaseConsumed() {
            return releaseConsumed;
        }

        /** Whether the server has run vanilla's {@code Item#releaseUsing} for this hold. */
        public boolean vanillaReleaseObserved() {
            return vanillaReleaseObserved;
        }

        /** Whether this hold is sustaining a spell clash, so its release casts nothing. */
        public boolean clashHold() {
            return clashHold;
        }
    }

    /**
     * Opens a session for a hold the server has just seen begin, replacing any previous one.
     *
     * <p>Only {@code WandItem.use} calls this, and only when that {@code use()} actually started a hold. A use
     * packet read while the server still holds the wand starts nothing in vanilla, and must not replace the
     * running hold's session either — that would throw away what the session knows about the hold, such as
     * that it is sustaining a spell clash.
     *
     * <p>Replacing a <em>finished</em> hold's session is deliberate: that hold is provably over once a new
     * hold has started from the same ordered connection, and its release — if it is still in flight — was
     * sent before the new {@code use()} and has therefore already been read.
     *
     * @param spellId the active spell as the hold begins; the release refuses to cast anything else
     * @return the new session's id
     */
    public static long begin(ServerPlayer player, long gameTick, @Nullable String spellId) {
        Session session = new Session(NEXT_ID.getAndIncrement(), gameTick, spellId);
        SESSIONS.put(player.getUUID(), session);
        return session.id();
    }

    /**
     * Offers a client release to the player's open session, spending its token when the release is valid.
     *
     * @return {@code null} when the release is accepted (and the token is now spent), otherwise the
     *         gate that refused it
     */
    @Nullable
    public static CastReleaseGate offerClientRelease(ServerPlayer player, long gameTick) {
        Session session = SESSIONS.get(player.getUUID());
        return offer(player, session, gameTick, session != null && session.vanillaReleaseObserved);
    }

    /**
     * Spends an open release token for a server-driven end (for example Avada Kedavra resolving on a
     * kill). This is deliberately the same token as {@link #offerClientRelease}; only the requirement
     * for an already-observed vanilla release is bypassed because the server itself is ending the hold.
     */
    @Nullable
    public static CastReleaseGate offerServerDrivenRelease(ServerPlayer player, long gameTick) {
        return offer(player, SESSIONS.get(player.getUUID()), gameTick, true);
    }

    @Nullable
    private static CastReleaseGate offer(ServerPlayer player, @Nullable Session session, long gameTick,
                                         boolean releaseConfirmed) {
        CastReleaseGate verdict = CastReleaseGate.evaluate(new CastReleaseGate.Inputs(
                player.isAlive(),
                session != null,
                session != null && session.releaseConsumed,
                releaseConfirmed,
                session == null ? 0L : gameTick - session.startGameTick,
                MAX_SESSION_TICKS,
                session != null && session.clashHold,
                session == null || Objects.equals(session.spellId, activeSpellId(player))));
        if (verdict != null && verdict.endsHoldWithoutCast()) {
            // The hold is over either way; spending the token makes a duplicate of this release read as one.
            session.releaseConsumed = true;
        }
        if (verdict != null) {
            return verdict;
        }
        // Non-null by construction: a null session yields NO_SESSION above.
        session.releaseConsumed = true;
        return null;
    }

    /**
     * Records vanilla's authoritative release callback. This must run before the client release
     * packet is accepted; connection ordering guarantees the packet follows this callback.
     */
    public static void markVanillaReleaseObserved(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null) {
            session.vanillaReleaseObserved = true;
        }
    }

    /** Whether a stop was preceded by vanilla's release callback rather than an interruption. */
    public static boolean hasVanillaReleaseObserved(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.vanillaReleaseObserved;
    }

    /**
     * Marks the player's open hold as sustaining a spell clash, so its release casts nothing. A no-op
     * without an open, unspent session — there is no hold to spend.
     */
    public static void markClashHold(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session != null && !session.releaseConsumed) {
            session.clashHold = true;
        }
    }

    /**
     * Whether the player's open, unreleased hold began with a different active spell than the one active now.
     *
     * <p>Such a hold can cast nothing (see {@link CastReleaseGate#SPELL_CHANGED}) and must drive no channel for
     * the new spell. A clash hold is exempt: it feeds the lock whichever spell is selected.
     */
    public static boolean spellChangedDuringHold(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null
                && !session.releaseConsumed
                && !session.vanillaReleaseObserved
                && !session.clashHold
                && !Objects.equals(session.spellId, activeSpellId(player));
    }

    /**
     * Drops the player's session. Every path that invalidates a caster mid-hold calls this — death,
     * respawn, dimension change, an interrupted hold — so a release that arrives afterwards meets IDLE
     * rather than a session the caster no longer has any claim to.
     */
    public static void abort(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    /** Whether the player's open hold is sustaining a spell clash. See {@link #markClashHold}. */
    public static boolean isClashHold(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.clashHold;
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

    private static @Nullable String activeSpellId(ServerPlayer player) {
        return player.getData(ModAttachments.SPELL_DATA.get()).getActiveSpellId();
    }

    /**
     * Holds the session to vanilla's item-use state once per server player tick.
     *
     * <p>{@code WandItem.onStopUsing} is the teardown for a hold that ends without a release, but vanilla does not
     * always call it: {@code LivingEntity.stopUsingItem} skips {@code onStopUsing} when the used stack is already
     * empty, and {@code ServerPlayer.drop} empties the wand <em>before</em> stopping the use. So dropping the wand
     * mid-hold — or anything else that empties the stack in place — ended the hold with its session and its beam
     * channel still open: a lifted target hung in the air and the next hold resumed the stale channel. This
     * reconciles the two from state rather than from a hook that can be skipped:
     *
     * <ul>
     *   <li>no wand in use → the hold is over; anything it left open that vanilla did not release is torn down;</li>
     *   <li>a wand in use whose hold began with another spell → the hold is ended, and the client, told by the
     *       synced use flag, lets go and presses afresh for the new spell.</li>
     * </ul>
     *
     * <p>A release vanilla has confirmed but whose packet has not arrived yet is left alone — that pending state is
     * exactly what a high-latency client's release lands on, possibly several ticks later.
     *
     * <p>Post, so it runs after the player's own tick has settled item use for this tick.
     */
    @EventBusSubscriber(modid = WizardsAndBeastsMod.MODID)
    public static final class Reconciliation {
        private Reconciliation() {}

        @SubscribeEvent
        public static void onPlayerTick(PlayerTickEvent.Post event) {
            if (!(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!WandItem.isUsingWand(player)) {
                WandItem.endHoldWithoutRelease(player);
            } else if (spellChangedDuringHold(player)) {
                // Through vanilla, so onStopUsing tears the hold down and the use flag reaches the client.
                player.stopUsingItem();
            }
        }
    }
}
