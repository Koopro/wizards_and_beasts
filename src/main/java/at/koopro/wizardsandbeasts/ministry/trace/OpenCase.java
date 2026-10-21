package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A case the Ministry has open against one wizard.
 *
 * <p>{@code lastKnown*} is where the most recent <em>report</em> put them, not where they are. Aurors search
 * there; a wizard who has moved on is not found until something new is reported.
 *
 * @param stage              where the case stands
 * @param incidents          the attributed incidents it is about, oldest first
 * @param stageSince         game tick the current stage began
 * @param deadline           game tick a summons must be answered by; 0 outside {@link CaseStage#SUMMONED}
 * @param lastKnownDimension dimension of the last report
 * @param lastKnownPos       position of the last report
 * @param nextSearch         game tick the Aurors next look; 0 outside {@link CaseStage#AURORS_ASSIGNED} or once cold
 * @param searches           searches that found nobody
 * @param failedToAppear     a summons deadline passed unanswered
 * @param caseId             the case's number, unique on the server; 0 on a case opened before numbers existed
 * @param cold               the Aurors stopped looking; a new report warms it again
 * @param auror              the player Auror working the case, if one has taken it
 * @param aurorUntil         game tick that Auror's hold on the case lapses
 */
@NullMarked
public record OpenCase(
        CaseStage stage,
        List<Long> incidents,
        long stageSince,
        long deadline,
        Identifier lastKnownDimension,
        BlockPos lastKnownPos,
        long nextSearch,
        int searches,
        boolean failedToAppear,
        long caseId,
        boolean cold,
        Optional<UUID> auror,
        long aurorUntil) {

    public static final Codec<OpenCase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CaseStage.CODEC.fieldOf("stage").forGetter(OpenCase::stage),
            Codec.LONG.listOf().optionalFieldOf("incidents", List.of()).forGetter(OpenCase::incidents),
            Codec.LONG.fieldOf("stage_since").forGetter(OpenCase::stageSince),
            Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(OpenCase::deadline),
            Identifier.CODEC.fieldOf("last_known_dimension").forGetter(OpenCase::lastKnownDimension),
            BlockPos.CODEC.fieldOf("last_known_pos").forGetter(OpenCase::lastKnownPos),
            Codec.LONG.optionalFieldOf("next_search", 0L).forGetter(OpenCase::nextSearch),
            Codec.INT.optionalFieldOf("searches", 0).forGetter(OpenCase::searches),
            Codec.BOOL.optionalFieldOf("failed_to_appear", false).forGetter(OpenCase::failedToAppear),
            Codec.LONG.optionalFieldOf("case_id", 0L).forGetter(OpenCase::caseId),
            Codec.BOOL.optionalFieldOf("cold", false).forGetter(OpenCase::cold),
            UUIDUtil.CODEC.optionalFieldOf("auror").forGetter(OpenCase::auror),
            Codec.LONG.optionalFieldOf("auror_until", 0L).forGetter(OpenCase::aurorUntil)
    ).apply(instance, OpenCase::new));

    public OpenCase {
        incidents = List.copyOf(incidents);
    }

    /** A case as it was before numbers, cold cases and player Aurors. */
    public OpenCase(CaseStage stage, List<Long> incidents, long stageSince, long deadline, Identifier lastKnownDimension,
                    BlockPos lastKnownPos, long nextSearch, int searches, boolean failedToAppear) {
        this(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch, searches,
                failedToAppear, 0L, false, Optional.empty(), 0L);
    }

    /** Adds an incident and moves the last known position to it. */
    public OpenCase withIncident(long incidentId, Identifier dimension, BlockPos pos) {
        List<Long> next = new ArrayList<>(incidents);
        if (!next.contains(incidentId)) {
            next.add(incidentId);
        }
        return new OpenCase(stage, next, stageSince, deadline, dimension, pos, nextSearch, searches, failedToAppear,
                caseId, cold, auror, aurorUntil);
    }

    public OpenCase withStage(CaseStage next, long since, long deadline, long nextSearch, boolean failedToAppear) {
        return new OpenCase(next, incidents, since, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                next == stage ? searches : 0, failedToAppear, caseId, cold, auror, aurorUntil);
    }

    public OpenCase afterFruitlessSearch(long nextSearch) {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                searches + 1, failedToAppear, caseId, cold, auror, aurorUntil);
    }

    /** The Aurors stop looking. The case stays open; nothing is forgotten. */
    public OpenCase goneCold() {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, 0L, searches,
                failedToAppear, caseId, true, auror, aurorUntil);
    }

    /** Wakes a cold case for the Aurors to look again at {@code nextSearch}. */
    public OpenCase warmed(long nextSearch) {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch, 0,
                failedToAppear, caseId, false, auror, aurorUntil);
    }

    public OpenCase withCaseId(long id) {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                searches, failedToAppear, id, cold, auror, aurorUntil);
    }

    /** Hands the case to a player Auror until {@code until}, or takes it back with an empty {@code holder}. */
    public OpenCase withAuror(Optional<UUID> holder, long until) {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                searches, failedToAppear, caseId, cold, holder, holder.isPresent() ? until : 0L);
    }

    /** The player Auror holding the case at {@code now}, if their hold has not lapsed. */
    public Optional<UUID> aurorAt(long now) {
        return auror.isPresent() && now < aurorUntil ? auror : Optional.empty();
    }
}
