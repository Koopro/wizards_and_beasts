package at.koopro.wizardsandbeasts.journey;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Apprentice's Path as rules: which steps apply to a player, which are met, which are new and which comes next.
 * Pure over its arguments; {@link ApprenticePathService} gathers the facts and pays.
 *
 * <p>Steps may be met in any order: a wizard who studies a bowtruckle before they own a wand has done that step. The
 * order only decides which unfinished step the path points at.
 */
@NullMarked
public final class ApprenticePath {

    private ApprenticePath() {}

    /**
     * What is true of a player now.
     *
     * @param wandHand   the heritage holds a wand and casts (or heritage is switched off), and wands and spells are on
     * @param bestiary   the bestiary is switched on
     * @param arrived    a heritage is chosen (or heritage is switched off, so there is no ceremony)
     * @param ownsWand   a wand that has chosen this player is in their inventory
     * @param hasCast    any spell has been cast
     * @param ownSpell   a spell beyond the starter charms is known
     * @param proficient any spell is at Proficient or better
     * @param studied    any creature's journal page is at OBSERVED or better
     */
    public record Facts(boolean wandHand, boolean bestiary, boolean arrived, boolean ownsWand, boolean hasCast,
                        boolean ownSpell, boolean proficient, boolean studied) {}

    public static boolean applies(PathStep step, Facts facts) {
        return switch (step.needs()) {
            case NOTHING -> true;
            case WAND_HAND -> facts.wandHand();
            case BESTIARY -> facts.bestiary();
        };
    }

    /** The steps this player walks, in path order. */
    public static List<PathStep> applicable(Facts facts) {
        List<PathStep> out = new ArrayList<>();
        for (PathStep step : PathStep.values()) {
            if (applies(step, facts)) {
                out.add(step);
            }
        }
        return out;
    }

    public static boolean met(PathStep step, Facts facts) {
        return switch (step) {
            case ARRIVE -> facts.arrived();
            case WAND -> facts.ownsWand();
            case FIRST_LIGHT -> facts.hasCast();
            case OWN_SPELL -> facts.ownSpell();
            case PRACTICE -> facts.proficient();
            case FIELD_STUDY -> facts.studied();
        };
    }

    /**
     * The applicable steps that are met now and not yet done, in path order. Every other step waits for the
     * ceremony: nothing on the path is paid to a player still standing in front of the gate.
     */
    public static List<PathStep> newlyMet(PathRecord record, Facts facts) {
        List<PathStep> out = new ArrayList<>();
        if (!facts.arrived() && !record.has(PathStep.ARRIVE)) {
            return out;
        }
        for (PathStep step : applicable(facts)) {
            if (!record.has(step) && met(step, facts)) {
                out.add(step);
            }
        }
        return out;
    }

    /** The first applicable step not yet done, or null once the path is walked. */
    public static @Nullable PathStep current(PathRecord record, Facts facts) {
        for (PathStep step : applicable(facts)) {
            if (!record.has(step)) {
                return step;
            }
        }
        return null;
    }

    public static boolean walked(PathRecord record, Facts facts) {
        return current(record, facts) == null;
    }
}
