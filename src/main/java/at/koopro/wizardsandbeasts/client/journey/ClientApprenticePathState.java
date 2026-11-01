package at.koopro.wizardsandbeasts.client.journey;

import at.koopro.wizardsandbeasts.chamber.ChamberStage;
import at.koopro.wizardsandbeasts.journey.PathStep;
import at.koopro.wizardsandbeasts.network.journey.ApprenticePathS2CPayload;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** The client's copy of the Apprentice's Path, as the server last sent it. Read by the character sheet. */
@NullMarked
public final class ClientApprenticePathState {

    /** One step as the sheet shows it. */
    public record Line(PathStep step, boolean done) {}

    private static List<Line> lines = List.of();
    private static Set<ChamberStage> chamber = Set.of();

    private ClientApprenticePathState() {}

    public static void set(ApprenticePathS2CPayload payload) {
        Set<String> done = Set.copyOf(payload.done());
        List<Line> out = new ArrayList<>();
        for (String id : payload.steps()) {
            PathStep step = PathStep.byId(id);
            // A step this client does not know (a newer server) is skipped rather than shown as a raw id.
            if (step != null) {
                out.add(new Line(step, done.contains(id)));
            }
        }
        lines = List.copyOf(out);
        Set<ChamberStage> stages = EnumSet.noneOf(ChamberStage.class);
        for (String id : payload.chamber()) {
            ChamberStage stage = ChamberStage.byId(id);
            if (stage != null) {
                stages.add(stage);
            }
        }
        chamber = stages;
    }

    /** True once every step of a path that is on is done. */
    public static boolean walked() {
        return !lines.isEmpty() && current() == null;
    }

    /** The Chamber stage to aim for next, or null when the quest has not begun or is over. */
    public static @Nullable ChamberStage chamberNext() {
        return ChamberStage.next(chamber);
    }

    /** The steps this player walks, in order; empty when the path is off or not yet heard of. */
    public static List<Line> lines() {
        return lines;
    }

    /** The first step not done, or null when every step is (or there are none). */
    public static @Nullable PathStep current() {
        for (Line line : lines) {
            if (!line.done()) {
                return line.step();
            }
        }
        return null;
    }

    public static void clear() {
        lines = List.of();
        chamber = Set.of();
    }
}
