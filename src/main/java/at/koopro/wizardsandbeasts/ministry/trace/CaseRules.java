package at.koopro.wizardsandbeasts.ministry.trace;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * How the Ministry answers an incident it can put a name to, and how an open case moves with time. Pure and
 * deterministic: every step is a stated rule on stated facts, so a player can be told why.
 *
 * <p>The ladder is record → warning → investigation → summons → Aurors, and an incident enters it at the
 * rung its facts earn — a child's second Lumos does not start with Aurors, and an Unforgivable does not start
 * with a warning letter. A case only ever climbs.
 */
@NullMarked
public final class CaseRules {

    /** How long an investigation takes before a hearing is called. */
    public static final int INVESTIGATION_TICKS = 6000;
    /** How long a summoned wizard has to answer — one in-game day. */
    public static final int SUMMONS_TICKS = 24000;
    /** How long Aurors take to reach a last known position, and to come back and look again. */
    public static final int AUROR_RESPONSE_TICKS = 1200;
    /** How far from the last reported position Aurors can find someone. */
    public static final double AUROR_SEARCH_RADIUS = 96.0;
    /** Secrecy breaches on file, counting this one, that turn a note into a warning and then a hearing. */
    public static final int BREACHES_BEFORE_WARNING = 2;
    public static final int BREACHES_BEFORE_HEARING = 3;

    private CaseRules() {}

    /** A decision on a newly attributed incident, with the dossier counters it leaves behind. */
    public record Decision(MinistryResponse response, int underageWarnings, int secrecyNotes) {}

    /**
     * The response an attributed incident earns.
     *
     * @param underageWarnings warning letters for underage magic already sent
     * @param secrecyNotes     secrecy breaches already on file
     */
    public static Decision decide(LegalClass legalClass, Exposure exposure, boolean underageBreach,
                                  int underageWarnings, int secrecyNotes) {
        int notes = secrecyNotes + (exposure.breachesSecrecy() ? 1 : 0);
        if (legalClass == LegalClass.UNFORGIVABLE || exposure == Exposure.EXTREME) {
            return new Decision(MinistryResponse.AURORS, underageWarnings, notes);
        }
        if (legalClass == LegalClass.DARK || exposure == Exposure.SEVERE) {
            return new Decision(MinistryResponse.INVESTIGATE, underageWarnings, notes);
        }
        if (underageBreach) {
            // Chamber of Secrets: a first offence is a letter; the second means a hearing.
            return underageWarnings == 0
                    ? new Decision(MinistryResponse.WARNING, underageWarnings + 1, notes)
                    : new Decision(MinistryResponse.SUMMON, underageWarnings, notes);
        }
        if (exposure.breachesSecrecy()) {
            if (notes >= BREACHES_BEFORE_HEARING) {
                return new Decision(MinistryResponse.SUMMON, underageWarnings, notes);
            }
            return new Decision(notes >= BREACHES_BEFORE_WARNING ? MinistryResponse.WARNING : MinistryResponse.RECORD,
                    underageWarnings, notes);
        }
        return new Decision(MinistryResponse.RECORD, underageWarnings, notes);
    }

    /**
     * Folds a decided incident into the wizard's case: opens one if the response needs it, adds the incident
     * to one already open, and raises the stage when the new incident is graver than the case so far.
     */
    public static Optional<OpenCase> fold(Optional<OpenCase> current, MinistryResponse response, long incidentId,
                                          Identifier dimension, BlockPos pos, long now) {
        CaseStage stage = response.stage();
        if (current.isEmpty()) {
            if (stage == null) {
                return Optional.empty();
            }
            return Optional.of(new OpenCase(stage, List.of(incidentId), now, deadlineFor(stage, now), dimension,
                    pos, searchFor(stage, now), 0, false));
        }
        OpenCase open = current.get().withIncident(incidentId, dimension, pos);
        if (stage != null && stage.ordinal() > open.stage().ordinal()) {
            open = open.withStage(stage, now, deadlineFor(stage, now), searchFor(stage, now), open.failedToAppear());
        }
        return Optional.of(open);
    }

    /** What the passage of time did to a case. */
    public enum Step {
        NOTHING,
        /** The investigation finished; a hearing is called. */
        SUMMONS_ISSUED,
        /** The summons went unanswered; Aurors take the case. */
        FAILED_TO_APPEAR,
        /** The Aurors are due to look for the wizard. */
        SEARCH_DUE
    }

    public record Advance(Step step, OpenCase openCase) {}

    public static Advance advance(OpenCase open, long now) {
        return switch (open.stage()) {
            case INVESTIGATING -> now >= open.stageSince() + INVESTIGATION_TICKS
                    ? new Advance(Step.SUMMONS_ISSUED, open.withStage(CaseStage.SUMMONED, now,
                            deadlineFor(CaseStage.SUMMONED, now), 0L, open.failedToAppear()))
                    : new Advance(Step.NOTHING, open);
            case SUMMONED -> now >= open.deadline()
                    ? new Advance(Step.FAILED_TO_APPEAR, open.withStage(CaseStage.AURORS_ASSIGNED, now, 0L,
                            searchFor(CaseStage.AURORS_ASSIGNED, now), true))
                    : new Advance(Step.NOTHING, open);
            case AURORS_ASSIGNED -> now >= open.nextSearch()
                    ? new Advance(Step.SEARCH_DUE, open)
                    : new Advance(Step.NOTHING, open);
        };
    }

    /**
     * Whether Aurors searching the last known position find the wizard: alive, in that dimension, and within
     * reach of where they were last reported. Where the wizard <em>is</em> is not something the Aurors know.
     */
    public static boolean aurorsFind(boolean alive, boolean sameDimension, double distanceSquared) {
        return alive && sameDimension && distanceSquared <= AUROR_SEARCH_RADIUS * AUROR_SEARCH_RADIUS;
    }

    /** When the Aurors look again after finding nobody. */
    public static long nextSearchAfter(long now) {
        return now + AUROR_RESPONSE_TICKS;
    }

    private static long deadlineFor(CaseStage stage, long now) {
        return stage == CaseStage.SUMMONED ? now + SUMMONS_TICKS : 0L;
    }

    private static long searchFor(CaseStage stage, long now) {
        return stage == CaseStage.AURORS_ASSIGNED ? now + AUROR_RESPONSE_TICKS : 0L;
    }
}
