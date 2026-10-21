package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What a creature is, ecologically: the reusable vocabulary {@link EcologyProfile} composes a creature from.
 *
 * <p>Each capability is either <b>driven</b> — {@link EcologyBrain} turns it into an intent the creature acts on — or
 * <b>perceptual</b> — it changes what {@code EcologySensing} lets the creature notice — or <b>descriptive</b> — it is
 * how the creature moves or lives, already carried by its entity class or abilities, and is declared so the
 * bestiary, the tests and the quality matrix can read it. {@link #kind()} says which; nothing here pretends to do
 * more than it does.
 *
 * <p>Serialised lower-case with hyphens ({@code "tree-dwelling"}), as the design documents write them.
 */
@NullMarked
public enum EcologyCapability implements StringRepresentable {
    /** Hunts {@link #PREY} and the {@code ecology_prey} tag when hungry; is fed for a while after a kill. */
    PREDATOR(Kind.DRIVEN),
    /** Flees anything that hunts it. */
    PREY(Kind.DRIVEN),
    /** Keeps close to its own kind and regroups when it strays. */
    HERD(Kind.DRIVEN),
    /** Holds ground around where it lives; warns intruders before it does anything else. */
    TERRITORIAL(Kind.DRIVEN),
    /** Rests by day, out of the sun when it can find cover; active at night. */
    NOCTURNAL(Kind.DRIVEN),
    /** Lives in water. Carried by the aquatic entity class. */
    AQUATIC(Kind.DESCRIPTIVE),
    /** Flies. Carried by the flying entity class. */
    FLYING(Kind.DESCRIPTIVE),
    /** Digs in and rests underground. */
    BURROWING(Kind.DESCRIPTIVE),
    /** Climbs walls and trunks. */
    CLIMBING(Kind.DESCRIPTIVE),
    /** Its home is a tree: it returns to the trunk it was found on. */
    TREE_DWELLING(Kind.DRIVEN),
    /** Notices magic: spells are noticed from twice as far. */
    MAGICAL_SENSE(Kind.PERCEPTUAL),
    /** Runs when badly hurt rather than fighting on. */
    COWARDLY(Kind.DRIVEN),
    /** Answers an intruder on its ground with force rather than a warning. */
    AGGRESSIVE(Kind.DRIVEN),
    /** Comes to look at quiet people and at magic. */
    CURIOUS(Kind.DRIVEN),
    /** Raises the alarm for its own kind when one of them is hurt. */
    SOCIAL(Kind.DRIVEN),
    /** Goes to food on the ground. */
    SCAVENGER(Kind.DRIVEN),
    /** Goes to gold and shiny things on the ground. */
    TREASURE_SEEKER(Kind.DRIVEN),
    /** Sees through invisibility and disguise. */
    ILLUSION_AWARE(Kind.PERCEPTUAL),
    /** Flees Dementors and dread. */
    FEAR_SENSITIVE(Kind.PERCEPTUAL),
    /** Keeps away from people it does not trust. */
    HUMAN_AVOIDANT(Kind.DRIVEN),
    /** Seeks out people. */
    HUMAN_FRIENDLY(Kind.DRIVEN),
    /** Shuns the dark-corrupted and those who have killed its kind. */
    MAGICAL_CORRUPTION_SENSITIVE(Kind.PERCEPTUAL);

    /** How a capability reaches play. */
    public enum Kind {
        /** Produces an {@link EcologyIntent}. */
        DRIVEN,
        /** Changes what the creature perceives. */
        PERCEPTUAL,
        /** Describes the body; carried by the entity class or abilities. */
        DESCRIPTIVE
    }

    public static final Codec<EcologyCapability> CODEC = StringRepresentable.fromEnum(EcologyCapability::values);

    private final Kind kind;

    EcologyCapability(Kind kind) {
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /** The capability a field note names, or {@code null} for a note this build does not know. */
    public static @org.jspecify.annotations.Nullable EcologyCapability fromNote(String note) {
        for (EcologyCapability capability : values()) {
            if (capability.getSerializedName().equals(note)) {
                return capability;
            }
        }
        return null;
    }

    /** Lang key for the bestiary's field-note line about this capability. */
    public String noteKey() {
        return "bestiary.wizards_and_beasts.ecology." + name().toLowerCase(Locale.ROOT);
    }
}
