package at.koopro.wizardsandbeasts.spell.cast;

import org.jspecify.annotations.Nullable;

/**
 * Pure decision logic for "is this wand release a real one?", evaluated before
 * {@link SpellCastService#completeWandCastRelease} is allowed to run.
 *
 * <p>The wand cast is a state machine whose only two transitions the network can observe are
 * <em>a hold began</em> and <em>the hold ended</em>. The hold beginning is a server-side fact —
 * {@code WandItem.use} calls {@code startUsingItem} on the server, and {@code onUseTick} drives the
 * beam from there — but the hold <em>ending</em> arrives as {@code SpellCastC2SPayload}, a packet with
 * no body, which the client sends whenever it likes. Everything a stale, duplicated or forged release
 * can do to this mod, it does through that one packet.
 *
 * <p>So the packet is treated as an <em>edge signal only</em>: it asserts nothing and carries nothing
 * the server has to trust. The server holds the session (see {@code WandCastSessions}), and this gate
 * decides whether the edge lands on one. The states, in the vocabulary of the cast:
 *
 * <pre>
 *   IDLE ──use() starts a hold──▶ CASTING ──onUseTick──▶ CHANNELING ──vanilla releaseUsing()──▶ RELEASE_PENDING
 *                                    │                       │                                      │
 *                                    └───────────────────────┴──▶ IDLE                    client release packet
 *                                       death · respawn · dimension · logout ·                      │
 *                                       hold ended without a release (slot swap,                    ▼
 *                                       dropped wand, stun, spell switched)            RELEASED ──▶ COOLDOWN
 *
 *   A server-driven release (Avada Kedavra on a kill) goes CHANNELING ──▶ RELEASED directly, then releases
 *   the item itself; the client's release for that hold then meets RELEASED.
 * </pre>
 *
 * <p>RELEASE_PENDING is a server observation, not a client claim: it exists only after vanilla calls
 * {@code Item#releaseUsing}. IDLE is "no session"; RELEASED is "the session's one release token is spent".
 * CASTING and CHANNELING are one session state — a channel is the beam logic acting on a hold, not a separate
 * edge. Both IDLE and RELEASED refuse a
 * release, which is what makes every hostile case below a single rule rather than a special case:
 *
 * <ul>
 *   <li><b>release without a cast</b> — no session was ever opened: {@link #NO_SESSION}</li>
 *   <li><b>duplicate release</b> — the token is spent: {@link #ALREADY_RELEASED}</li>
 *   <li><b>release after cancellation, death, respawn or a dimension change</b> — those paths abort
 *       the session, so the packet arrives at IDLE: {@link #NO_SESSION}</li>
 *   <li><b>an old session's release arriving after a new cast began</b> — the connection is ordered,
 *       so a packet sent before the new {@code use()} is <em>read</em> before it too, and it meets
 *       the old session's spent token. A client that withholds one instead is indistinguishable from
 *       one that releases late, which is the duplicate case again.</li>
 *   <li><b>letting go of a spell clash</b> — the hold was feeding a lock, not charging a cast:
 *       {@link #CLASH_HOLD}</li>
 *   <li><b>a client release before vanilla sees the hold end</b> — it is not a release at all:
 *       {@link #RELEASE_NOT_CONFIRMED}</li>
 *   <li><b>switching spell before letting go</b> — the hold was charged for another spell:
 *       {@link #SPELL_CHANGED}</li>
 * </ul>
 *
 * <p>No wire sequence number is needed for that last case and none is sent. A number the client
 * chooses is a number the server would have to validate, and the ordering guarantee already decides
 * it; the safest thing to put on the wire here is nothing at all.
 *
 * <p>This deliberately holds no timing window. The guard it replaced ignored releases for a fixed
 * fifteen ticks after a server-driven one, which both let a genuine re-press inside the window be
 * eaten and let a duplicate outside it through — a delay standing in for the state it could not see.
 */
public enum CastReleaseGate {
    /** No hold is open for this player: nothing was cast, or the session was aborted. */
    NO_SESSION,
    /** This hold's single release has already been spent (duplicate packet, or a server-driven release). */
    ALREADY_RELEASED,
    /** The client packet arrived before vanilla confirmed that this server-side hold had ended. */
    RELEASE_NOT_CONFIRMED,
    /** The caster is dead. Vanilla ends the hold client-side on death, and that release must not land. */
    CASTER_NOT_ALIVE,
    /**
     * The session is older than the wand's own declared use duration, so no live hold can correspond
     * to it. Not a tuning window — past this point vanilla has already force-released the item.
     */
    SESSION_EXPIRED,
    /**
     * The hold was spent sustaining a spell clash. A hold that begins inside a lock, or is already open
     * when one starts, feeds the lock and nothing else — letting go is giving the lock up, not casting.
     * See {@code SpellClashLocks}.
     */
    CLASH_HOLD,
    /**
     * The active spell is not the one this hold began with. Everything a hold accumulates — its charge, a
     * channel's effects — belongs to the spell it was pressed for, so the release casts neither: letting go of
     * a long Lumos hold must not come out as a fully charged Protego. The server also ends such a hold on its own
     * tick, so this verdict is what meets a switch and a release read in the same drain.
     */
    SPELL_CHANGED;

    /**
     * Side-effect-free facts about a release attempt. Read in precedence order, short-circuiting at
     * the first failure.
     *
     * @param casterAlive             the releasing player is alive
     * @param sessionOpen             a wand hold was opened server-side and has not been aborted
     * @param releaseAlreadyConsumed  that session's release token is already spent
     * @param vanillaReleaseObserved  vanilla {@code Item#releaseUsing} has run for this hold
     * @param ticksSinceSessionStart  game ticks between the session opening and this release
     * @param maxSessionTicks         the wand's declared use duration; see {@code WandItem}
     * @param clashHold               that session was spent holding a spell clash
     * @param holdSpellStillActive    the active spell is the one the hold began with
     */
    public record Inputs(boolean casterAlive,
                         boolean sessionOpen,
                         boolean releaseAlreadyConsumed,
                         boolean vanillaReleaseObserved,
                         long ticksSinceSessionStart,
                         long maxSessionTicks,
                         boolean clashHold,
                         boolean holdSpellStillActive) {}

    /** The first failing gate, or {@code null} when the release may resolve into a cast. */
    @Nullable
    public static CastReleaseGate evaluate(Inputs in) {
        // Ahead of the session checks: a dead player's release is refused whether or not a session
        // survived them, so the answer does not depend on which lifecycle hook ran first.
        if (!in.casterAlive()) return CASTER_NOT_ALIVE;
        if (!in.sessionOpen()) return NO_SESSION;
        if (in.releaseAlreadyConsumed()) return ALREADY_RELEASED;
        if (!in.vanillaReleaseObserved()) return RELEASE_NOT_CONFIRMED;
        // A negative age means the session was stamped from a clock ahead of this one — an impossible
        // state rather than an old session, and refused as firmly.
        if (in.ticksSinceSessionStart() < 0 || in.ticksSinceSessionStart() > in.maxSessionTicks()) {
            return SESSION_EXPIRED;
        }
        if (in.clashHold()) return CLASH_HOLD;
        if (!in.holdSpellStillActive()) return SPELL_CHANGED;
        return null;
    }

    /**
     * Whether this verdict is a real release of a hold that is simply worth nothing — as opposed to a packet the
     * server could not match to a release. Such a verdict spends the hold's token, so a duplicate of the same
     * release reads as a duplicate, and it is the game working rather than the client and server disagreeing.
     */
    public boolean endsHoldWithoutCast() {
        return this == CLASH_HOLD || this == SPELL_CHANGED;
    }

    /** The telemetry / denial code this verdict is recorded under. */
    public String rejectCode() {
        return switch (this) {
            case NO_SESSION -> SpellRejectCodes.NO_CAST_SESSION;
            case ALREADY_RELEASED -> SpellRejectCodes.DUPLICATE_RELEASE_GUARD;
            case RELEASE_NOT_CONFIRMED -> SpellRejectCodes.RELEASE_NOT_CONFIRMED;
            case CASTER_NOT_ALIVE -> SpellRejectCodes.CASTER_NOT_ALIVE;
            case SESSION_EXPIRED -> SpellRejectCodes.CAST_SESSION_EXPIRED;
            case CLASH_HOLD -> SpellRejectCodes.CLASH_HOLD;
            case SPELL_CHANGED -> SpellRejectCodes.SPELL_CHANGED_DURING_HOLD;
        };
    }
}
