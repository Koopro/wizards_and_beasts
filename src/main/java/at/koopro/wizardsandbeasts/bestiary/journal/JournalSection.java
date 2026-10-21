package at.koopro.wizardsandbeasts.bestiary.journal;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * Every kind of thing a journal page can say about a creature. Which ones a page shows, where, and from which depth
 * of knowledge is {@link JournalLayout}'s business (datapack); what each one says is drawn from the entry, the
 * server-derived {@link JournalFacts}, and the player's own notes and history.
 */
@NullMarked
public enum JournalSection implements StringRepresentable {
    /** Ministry division (Beast, Being, …) and its XXXXX rating. */
    CLASSIFICATION,
    /** How big it is. */
    SIZE,
    /** Where it lives — where you first saw it, until you have watched it long enough to know. */
    HABITAT,
    /** Its disposition, derived from its creature data and ecology. */
    TEMPERAMENT,
    /** What it can do to you. */
    DANGER,
    /** What the books say it does. */
    KNOWN_BEHAVIOUR,
    /** What you have seen it do: the field notes. */
    OBSERVED_BEHAVIOUR,
    /** What it eats. */
    DIET,
    /** What it will take from your hand, and whether it can come to trust you. */
    FEEDING_BONDING,
    /** Its magic. */
    MAGIC,
    /** What it can lose to careful hands, from what depth of knowledge, how often. */
    HARVESTING,
    /** What wizards make from it, and how each part is properly come by. */
    MATERIALS,
    /** What it fears or cannot abide. */
    WEAKNESSES,
    /** The full account: wizarding-world knowledge and its place in wizarding society. */
    LORE,
    /** The thing it is famous for. */
    SIGNATURE,
    /** When and where you met it, and when you learned each thing. */
    HISTORY,
    /** How far your research has got, and what is left. */
    RESEARCH;

    public static final Codec<JournalSection> CODEC = StringRepresentable.fromEnum(JournalSection::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The heading's lang key. */
    public String headingKey() {
        return "bestiary.wizards_and_beasts.journal.section." + getSerializedName();
    }
}
