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
 * character keeps their heritage and lineage and carries the condition on top of it.
 *
 * <p>A condition has an {@link ConditionOrigin}: how it came about, which shapes it.
 */
@NullMarked
public enum MagicalCondition {

    /** Bitten by a werewolf; the full moon claims them. */
    LYCANTHROPY("lycanthropy"),
    /** An Obscurus lives in them. */
    OBSCURUS("obscurus");

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
     * <p>Both conditions belong to witches and wizards. A bite passes lycanthropy to anyone human, a Squib included —
     * it is a curse of the body, not of magic. An Obscurus is the opposite: it grows out of magic that was forced
     * down, so there has to be magic there to force, which leaves the Squib out.
     */
    public boolean canBeCarriedBy(@Nullable Heritage heritage, @Nullable HeritageVariant variant) {
        if (heritage != Heritage.WIZARDKIND) {
            return false;
        }
        return this != OBSCURUS || variant == null || !variant.hasTag("no_casting");
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
