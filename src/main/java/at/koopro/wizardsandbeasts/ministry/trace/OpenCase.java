package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

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
 * @param nextSearch         game tick the Aurors next look; 0 outside {@link CaseStage#AURORS_ASSIGNED}
 * @param searches           searches that found nobody
 * @param failedToAppear     a summons deadline passed unanswered
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
        boolean failedToAppear) {

    public static final Codec<OpenCase> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            CaseStage.CODEC.fieldOf("stage").forGetter(OpenCase::stage),
            Codec.LONG.listOf().optionalFieldOf("incidents", List.of()).forGetter(OpenCase::incidents),
            Codec.LONG.fieldOf("stage_since").forGetter(OpenCase::stageSince),
            Codec.LONG.optionalFieldOf("deadline", 0L).forGetter(OpenCase::deadline),
            Identifier.CODEC.fieldOf("last_known_dimension").forGetter(OpenCase::lastKnownDimension),
            BlockPos.CODEC.fieldOf("last_known_pos").forGetter(OpenCase::lastKnownPos),
            Codec.LONG.optionalFieldOf("next_search", 0L).forGetter(OpenCase::nextSearch),
            Codec.INT.optionalFieldOf("searches", 0).forGetter(OpenCase::searches),
            Codec.BOOL.optionalFieldOf("failed_to_appear", false).forGetter(OpenCase::failedToAppear)
    ).apply(instance, OpenCase::new));

    public OpenCase {
        incidents = List.copyOf(incidents);
    }

    /** Adds an incident and moves the last known position to it. */
    public OpenCase withIncident(long incidentId, Identifier dimension, BlockPos pos) {
        List<Long> next = new ArrayList<>(incidents);
        if (!next.contains(incidentId)) {
            next.add(incidentId);
        }
        return new OpenCase(stage, next, stageSince, deadline, dimension, pos, nextSearch, searches, failedToAppear);
    }

    public OpenCase withStage(CaseStage next, long since, long deadline, long nextSearch, boolean failedToAppear) {
        return new OpenCase(next, incidents, since, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                next == stage ? searches : 0, failedToAppear);
    }

    public OpenCase afterFruitlessSearch(long nextSearch) {
        return new OpenCase(stage, incidents, stageSince, deadline, lastKnownDimension, lastKnownPos, nextSearch,
                searches + 1, failedToAppear);
    }
}
