package at.koopro.wizardsandbeasts.ministry.trace;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.UUID;

/**
 * Everything about the moment a spell was cast that the law could care about, surveyed once on the server.
 *
 * <p>Pure data, so {@link TraceRules} can be tested with a hand-written scene and no world.
 *
 * @param underage              the caster is under seventeen
 * @param inDanger              the caster was badly hurt or being hunted — the Decree's life-threatening exception
 * @param atHogwarts            cast inside Hogwarts, where students may use magic and no Muggle can see
 * @param muggleWitnesses       Muggles with a line of sight to the caster
 * @param adultWizardsNearby    other wizards of age close enough that the magic could have been theirs
 * @param officialsWatching     Ministry officials with a line of sight to the caster
 * @param dangerousCreatureSeen a creature the Ministry classifies XXXX or above was in view of a Muggle
 * @param wizardWitnesses       other players who saw it; the only players who may later testify about it
 * @param witnessEntities       the Muggles and officials who saw it, so an Auror can find and question them
 * @param licensedPractice      the caster held a usable Restricted Magic licence
 */
@NullMarked
public record CastScene(
        boolean underage,
        boolean inDanger,
        boolean atHogwarts,
        int muggleWitnesses,
        int adultWizardsNearby,
        int officialsWatching,
        boolean dangerousCreatureSeen,
        List<UUID> wizardWitnesses,
        List<UUID> witnessEntities,
        boolean licensedPractice) {

    public CastScene {
        muggleWitnesses = Math.max(0, muggleWitnesses);
        adultWizardsNearby = Math.max(0, adultWizardsNearby);
        officialsWatching = Math.max(0, officialsWatching);
        wizardWitnesses = List.copyOf(wizardWitnesses);
        witnessEntities = List.copyOf(witnessEntities);
    }

    /** A scene with no witness entities on record and no licence. */
    public CastScene(boolean underage, boolean inDanger, boolean atHogwarts, int muggleWitnesses,
                     int adultWizardsNearby, int officialsWatching, boolean dangerousCreatureSeen,
                     List<UUID> wizardWitnesses) {
        this(underage, inDanger, atHogwarts, muggleWitnesses, adultWizardsNearby, officialsWatching,
                dangerousCreatureSeen, wizardWitnesses, List.of(), false);
    }

    /** An adult alone in the wilderness. */
    public static CastScene alone() {
        return new CastScene(false, false, false, 0, 0, 0, false, List.of());
    }
}
