package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Who may do what on a case. Pure: every check takes the facts it rules on, so the server's answer to "may this
 * player arrest that one" is a function anyone can read and a test can pin.
 *
 * <p>The guarantees that make player Aurors safe on a shared server:
 * <ul>
 *   <li>Nobody works a case about themselves, or one they are a witness to — they may testify, not investigate.</li>
 *   <li>An Auror's work only <em>adds server-observed evidence</em>: they choose where to look, never what is
 *       found. Nothing here lets anyone remove evidence, open a case, or name a suspect.</li>
 *   <li>Testimony is only from a player the server recorded seeing the incident, once each.</li>
 *   <li>An apprehension needs a case at {@link CaseStage#AURORS_ASSIGNED} held by that Auror, the suspect close,
 *       alive and either downed or giving themselves up.</li>
 * </ul>
 */
@NullMarked
public final class CaseworkRules {

    /** Cases one Auror may hold at once. */
    public static final int MAX_HELD = 3;
    /** How long an Auror holds a case before it returns to the board: three in-game days. */
    public static final long HOLD_TICKS = 72000L;
    /** How close to the scene an examination must be made. */
    public static final double EXAMINE_RADIUS = 12.0;
    /** How close to a witness an interview is. */
    public static final double INTERVIEW_RADIUS = 6.0;
    /** How close to a suspect an apprehension is. */
    public static final double APPREHEND_RADIUS = 8.0;
    /** A suspect at or under this fraction of their health is downed. */
    public static final float DOWNED_FRACTION = 0.3f;

    /** The answer to a request, {@link #OK} or why not. Each non-OK value has a lang line. */
    public enum Result {
        OK,
        DISABLED,
        NOT_AUROR,
        NO_SUCH_CASE,
        OWN_CASE,
        WITNESS_TO_CASE,
        HELD_BY_ANOTHER,
        NOT_HOLDER,
        TOO_MANY_CASES,
        TOO_FAR,
        WRONG_STAGE,
        STALE_SCENE,
        ALREADY_EXAMINED,
        NOT_A_WITNESS,
        NOT_CREDIBLE,
        ALREADY_GIVEN,
        NOT_DOWNED,
        SUSPECT_GONE;

        public boolean ok() {
            return this == OK;
        }
    }

    private CaseworkRules() {}

    /**
     * Whether {@code auror} may take a case.
     *
     * @param subject   who the case is about — the server's knowledge, used only to refuse a conflict
     * @param witnesses players recorded as eyewitnesses to any incident on the case
     * @param holder    who holds it now, if anyone
     * @param held      cases {@code auror} already holds
     */
    public static Result take(UUID auror, boolean isAuror, Optional<UUID> subject, Collection<UUID> witnesses,
                              Optional<UUID> holder, int held) {
        if (!isAuror) {
            return Result.NOT_AUROR;
        }
        if (subject.isPresent() && subject.get().equals(auror)) {
            return Result.OWN_CASE;
        }
        if (witnesses.contains(auror)) {
            return Result.WITNESS_TO_CASE;
        }
        if (holder.isPresent()) {
            return holder.get().equals(auror) ? Result.OK : Result.HELD_BY_ANOTHER;
        }
        return held >= MAX_HELD ? Result.TOO_MANY_CASES : Result.OK;
    }

    /** Whether the holder may examine the scene of an incident from where they stand. */
    public static Result examine(boolean holds, boolean sameDimension, double distanceSquared, long castTick, long now,
                                 boolean alreadyExamined) {
        if (!holds) {
            return Result.NOT_HOLDER;
        }
        if (alreadyExamined) {
            return Result.ALREADY_EXAMINED;
        }
        if (!sameDimension || distanceSquared > EXAMINE_RADIUS * EXAMINE_RADIUS) {
            return Result.TOO_FAR;
        }
        return EvidenceRules.sceneFresh(castTick, now) ? Result.OK : Result.STALE_SCENE;
    }

    /** Whether the holder may interview this witness. */
    public static Result interview(boolean holds, boolean recordedWitness, boolean alive, double distanceSquared,
                                   boolean alreadyGiven) {
        if (!holds) {
            return Result.NOT_HOLDER;
        }
        if (!recordedWitness) {
            return Result.NOT_A_WITNESS;
        }
        if (alreadyGiven) {
            return Result.ALREADY_GIVEN;
        }
        if (!alive || distanceSquared > INTERVIEW_RADIUS * INTERVIEW_RADIUS) {
            return Result.TOO_FAR;
        }
        return Result.OK;
    }

    /**
     * Whether a player may testify about an incident. Only the server's record of who saw it counts; the caster
     * cannot testify against themselves, each witness speaks once, and a wizard the public despises is not believed.
     */
    public static Result testify(UUID witness, UUID caster, Collection<UUID> recordedWitnesses, boolean alreadyGiven,
                                 boolean credible) {
        if (witness.equals(caster)) {
            return Result.OWN_CASE;
        }
        if (!recordedWitnesses.contains(witness)) {
            return Result.NOT_A_WITNESS;
        }
        if (alreadyGiven) {
            return Result.ALREADY_GIVEN;
        }
        return credible ? Result.OK : Result.NOT_CREDIBLE;
    }

    /** Whether the holder may take the suspect in. */
    public static Result apprehend(boolean holds, CaseStage stage, boolean suspectAlive, boolean sameDimension,
                                   double distanceSquared, boolean downed, boolean surrendering) {
        if (!holds) {
            return Result.NOT_HOLDER;
        }
        if (stage != CaseStage.AURORS_ASSIGNED) {
            return Result.WRONG_STAGE;
        }
        if (!suspectAlive) {
            return Result.SUSPECT_GONE;
        }
        if (!sameDimension || distanceSquared > APPREHEND_RADIUS * APPREHEND_RADIUS) {
            return Result.TOO_FAR;
        }
        return downed || surrendering ? Result.OK : Result.NOT_DOWNED;
    }

    /** Whether a suspect is down: badly hurt, or stunned. */
    public static boolean downed(float health, float maxHealth, boolean stunned) {
        return stunned || (maxHealth > 0.0f && health <= maxHealth * DOWNED_FRACTION);
    }
}
