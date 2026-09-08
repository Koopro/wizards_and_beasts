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
 *   IDLE ──use()──▶ CASTING ──onUseTick──▶ CHANNELING ──release packet──▶ RELEASED ──▶ COOLDOWN
 *                      │                        │
 *                      └────────────────────────┴── death / respawn / dimension / logout ──▶ IDLE
 * </pre>
 *
 * <p>IDLE is "no session"; RELEASED is "the session's one release token is spent". Both refuse a
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
    /** The caster is dead. Vanilla ends the hold client-side on death, and that release must not land. */
    CASTER_NOT_ALIVE,
    /**
     * The session is older than the wand's own declared use duration, so no live hold can correspond
     * to it. Not a tuning window — past this point vanilla has already force-released the item.
     */
    SESSION_EXPIRED;

    /**
     * Side-effect-free facts about a release attempt. Read in precedence order, short-circuiting at
     * the first failure.
     *
     * @param casterAlive             the releasing player is alive
     * @param sessionOpen             a wand hold was opened server-side and has not been aborted
     * @param releaseAlreadyConsumed  that session's release token is already spent
     * @param ticksSinceSessionStart  game ticks between the session opening and this release
     * @param maxSessionTicks         the wand's declared use duration; see {@code WandItem}
     */
    public record Inputs(boolean casterAlive,
                         boolean sessionOpen,
                         boolean releaseAlreadyConsumed,
                         long ticksSinceSessionStart,
                         long maxSessionTicks) {}

    /** The first failing gate, or {@code null} when the release may resolve into a cast. */
    @Nullable
    public static CastReleaseGate evaluate(Inputs in) {
        // Ahead of the session checks: a dead player's release is refused whether or not a session
        // survived them, so the answer does not depend on which lifecycle hook ran first.
        if (!in.casterAlive()) return CASTER_NOT_ALIVE;
        if (!in.sessionOpen()) return NO_SESSION;
        if (in.releaseAlreadyConsumed()) return ALREADY_RELEASED;
        // A negative age means the session was stamped from a clock ahead of this one — an impossible
        // state rather than an old session, and refused as firmly.
        if (in.ticksSinceSessionStart() < 0 || in.ticksSinceSessionStart() > in.maxSessionTicks()) {
            return SESSION_EXPIRED;
        }
        return null;
    }

    /** The telemetry / denial code this verdict is recorded under. */
    public String rejectCode() {
        return switch (this) {
            case NO_SESSION -> SpellRejectCodes.NO_CAST_SESSION;
            case ALREADY_RELEASED -> SpellRejectCodes.DUPLICATE_RELEASE_GUARD;
            case CASTER_NOT_ALIVE -> SpellRejectCodes.CASTER_NOT_ALIVE;
            case SESSION_EXPIRED -> SpellRejectCodes.CAST_SESSION_EXPIRED;
        };
    }
}
