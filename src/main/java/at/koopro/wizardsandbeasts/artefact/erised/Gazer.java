package at.koopro.wizardsandbeasts.artefact.erised;

import at.koopro.wizardsandbeasts.heritage.Heritage;
import at.koopro.wizardsandbeasts.heritage.HeritageVariant;
import at.koopro.wizardsandbeasts.heritage.MagicalCondition;
import at.koopro.wizardsandbeasts.owl.Profession;
import at.koopro.wizardsandbeasts.owl.career.track.CareerContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Everything a vision may ask about the person looking into the Mirror, read once per gaze.
 *
 * <p>A plain record so that which vision a player sees can be decided -- and tested -- without a world: the
 * server fills it from the player's attachments ({@link ErisedService}), a unit test fills it by hand.
 *
 * @param lostCompanions names of the player's companions that have died, most recent first
 * @param spellsKnown    how many spells the player knows
 * @param creaturesKnown bestiary pages at Observed or deeper
 */
@NullMarked
public record Gazer(@Nullable Heritage heritage, @Nullable HeritageVariant variant, Set<MagicalCondition> conditions,
                    List<String> lostCompanions, int spellsKnown, int creaturesKnown,
                    @Nullable Profession profession, CareerContext career) {

    public Gazer {
        conditions = Set.copyOf(conditions);
        lostCompanions = List.copyOf(lostCompanions);
    }
}
