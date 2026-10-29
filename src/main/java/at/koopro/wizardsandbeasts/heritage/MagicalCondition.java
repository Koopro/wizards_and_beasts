package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Something that has happened to a witch or wizard, as opposed to something they are.
 *
 * <p>Canon is clear on both. Lycanthropy is a curse passed on by a bite: Remus Lupin is a wizard, a Gryffindor, a
 * teacher — and a werewolf. An Obscurus grows in a magical child who suppressed their magic: Credence Barebone is a
 * wizard whose magic turned on him. Neither is a people with its own lineages, so neither is a {@link Heritage}; a
 * character keeps their heritage and lineage and carries the condition on top of it. Vampirism acquired in play is the
 * same shape: a wizard or a Veela turned by a sire stays who they were, with the thirst, the sun and the bat added. The
 * Vampire people ({@link Heritage#VAMPIRE}) remains for those born to it.
 *
 * <p>A condition has an {@link ConditionOrigin}: how it came about, which shapes it.
 */
@NullMarked
public enum MagicalCondition {

    /** Bitten by a werewolf; the full moon claims them. */
    LYCANTHROPY("lycanthropy"),
    /** An Obscurus lives in them. */
    OBSCURUS("obscurus"),
    /** A vampire's blood took them; the sun is no friend of theirs now, and food does nothing. */
    VAMPIRISM("vampirism");

    private final String id;

    MagicalCondition(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public String getTranslationKey() {
        return "condition." + WizardsAndBeastsMod.MODID + "." + id;
    }

    public String getDescriptionTranslationKey() {
        return getTranslationKey() + ".desc";
    }

    /** The ways this condition comes about. */
    public List<ConditionOrigin> origins() {
        return java.util.Arrays.stream(ConditionOrigin.values()).filter(origin -> origin.condition() == this).toList();
    }

    /**
     * Whether a character of this heritage and lineage could carry this condition.
     *
     * <p>Lycanthropy and the Obscurus belong to witches and wizards. A bite passes lycanthropy to anyone human, a Squib
     * included — it is a curse of the body, not of magic. An Obscurus is the opposite: it grows out of magic that was
     * forced down, so there has to be magic there to force, which leaves the Squib out. Vampirism may be given to
     * Wizardkind and to Veela, and to no other people (Heritage 2.0's ruling); like the bite it takes the body, so a
     * Squib can be turned.
     */
    public boolean canBeCarriedBy(@Nullable Heritage heritage, @Nullable HeritageVariant variant) {
        return switch (this) {
            case LYCANTHROPY -> heritage == Heritage.WIZARDKIND;
            case OBSCURUS -> heritage == Heritage.WIZARDKIND && (variant == null || !variant.hasTag("no_casting"));
            case VAMPIRISM -> heritage == Heritage.WIZARDKIND || heritage == Heritage.VEELA;
        };
    }

    public static @Nullable MagicalCondition byId(@Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (MagicalCondition condition : values()) {
            if (condition.id.equals(id.toLowerCase(Locale.ROOT))) {
                return condition;
            }
        }
        return null;
    }
}
