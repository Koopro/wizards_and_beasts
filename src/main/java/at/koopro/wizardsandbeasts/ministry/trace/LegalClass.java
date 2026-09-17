package at.koopro.wizardsandbeasts.ministry.trace;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What the law thinks of a spell in itself, before anyone asks where it was cast or who saw.
 *
 * <p>Most magic is not a crime. A wizard of age may light a wand, summon a book or stun a duellist; the law
 * cares about those only when a Muggle sees, or when a child does it outside school. Only the three
 * Unforgivable Curses are crimes wherever and however they are cast ("use of any one of them on a fellow
 * human being is enough to earn a life sentence in Azkaban", <i>Goblet of Fire</i> ch. 14).
 */
@NullMarked
public enum LegalClass implements StringRepresentable {

    /** Everyday magic: Lumos, Accio, Reparo. */
    UNRESTRICTED(0),
    /** Magic meant to hurt or overpower — hexes, jinxes, Stupefy. Legal between wizards; a weight in a case. */
    RESTRICTED(1),
    /** Dark magic that is not Unforgivable. Not a crime on its own in canon; it makes every other fact worse. */
    DARK(2),
    /** Avada Kedavra, Crucio, Imperio. A crime in itself. */
    UNFORGIVABLE(4);

    public static final Codec<LegalClass> CODEC = StringRepresentable.fromEnum(LegalClass::values);

    private final int gravity;

    LegalClass(int gravity) {
        this.gravity = gravity;
    }

    /** How much this class adds to the weight of an incident. */
    public int gravity() {
        return gravity;
    }

    /** Dark and Unforgivable magic leave something a wand examination can find. */
    public boolean leavesResidue() {
        return this == DARK || this == UNFORGIVABLE;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
