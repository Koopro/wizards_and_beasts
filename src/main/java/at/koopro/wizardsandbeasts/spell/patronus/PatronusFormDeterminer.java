package at.koopro.wizardsandbeasts.spell.patronus;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Which animal a witch's or wizard's Patronus takes.
 *
 * <p>A Patronus belongs to the person who casts it. It is not inherited and not chosen: Harry's stag was his
 * father's shape because of who Harry was, not because of his blood; Snape's doe matched Lily's (<i>Deathly
 * Hallows</i> ch. 33); Tonks's changed with her heart (<i>Half-Blood Prince</i>). So the form is drawn once per
 * character from a stable seed of their own identity, and the same character always gets the same animal.
 *
 * <p>It used to be decided by heritage and blood status — pure-blood wolf, half-blood fox, Muggle-born rabbit — with
 * a "rare" form unlocked by happiness, and no Patronus at all for most heritages. Sorting a soul by its ancestry is
 * the pure-blood idea the books condemn, and nothing in canon ties a Patronus to lineage
 * (documentation/CANON_AUDIT.md C-1). A form already stored on a player is kept; this decides only first forms.
 *
 * <p>The animals are vanilla stand-ins drawn by the existing Patronus renderer.
 */
@NullMarked
public final class PatronusFormDeterminer {

    /** The animals a Patronus can take. Order is part of the save contract: append, never reorder. */
    public static final List<Identifier> FORMS = List.of(
            mc("horse"), mc("wolf"), mc("fox"), mc("rabbit"),
            mc("cat"), mc("goat"), mc("parrot"), mc("bat"));

    private PatronusFormDeterminer() {}

    /** This character's Patronus form: the same every time for the same character, whatever their heritage. */
    public static Identifier determine(UUID characterId) {
        long seed = characterId.getMostSignificantBits() ^ Long.rotateLeft(characterId.getLeastSignificantBits(), 17);
        return FORMS.get(new Random(seed).nextInt(FORMS.size()));
    }

    private static Identifier mc(String path) {
        return Identifier.fromNamespaceAndPath("minecraft", path);
    }
}
