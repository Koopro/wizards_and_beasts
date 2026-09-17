package at.koopro.wizardsandbeasts.heritage;

import at.koopro.wizardsandbeasts.WizardsAndBeastsMod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * How a {@link MagicalCondition} came about. The ids are the ones the old werewolf and obscurial "lineages" were saved
 * under, so a save written before conditions existed maps straight onto one.
 *
 * <p>{@code traits} use the same vocabulary as a lineage's tags ({@link HeritageTraits}), and a character's traits are
 * the union of the two.
 */
@NullMarked
public enum ConditionOrigin {

    // ── lycanthropy ──
    /** Bitten as an adult. Remus Lupin's dignity is the measure of how such a life can be lived. */
    BITTEN("bitten", MagicalCondition.LYCANTHROPY, Set.of("moon_sensitive", "transformation")),
    /** Bitten as a child, like Lupin at four; the wolf is the oldest thing they remember. */
    CRADLE_BITTEN("born", MagicalCondition.LYCANTHROPY, Set.of("moon_sensitive", "transformation")),
    /** Torn deliberately by a werewolf who meant to spread the curse, as Greyback did. */
    SAVAGE_BITTEN("savage_bite", MagicalCondition.LYCANTHROPY, Set.of("moon_sensitive", "transformation")),

    // ── the Obscurus ──
    /** Held beneath the ribs like a breath: present, hungry, not yet loose. */
    SUPPRESSED("suppressed", MagicalCondition.OBSCURUS, Set.of("obscurus_form", "no_casting", "no_wand")),
    /** Host and parasite braided so tight that windows break when composure does. */
    UNLEASHED("unleashed", MagicalCondition.OBSCURUS, Set.of("obscurus_form", "no_casting", "no_wand", "transformation"));

    private final String id;
    private final MagicalCondition condition;
    private final Set<String> traits;

    ConditionOrigin(String id, MagicalCondition condition, Set<String> traits) {
        this.id = id;
        this.condition = condition;
        this.traits = traits;
    }

    public String getId() {
        return id;
    }

    public MagicalCondition condition() {
        return condition;
    }

    public Set<String> traits() {
        return traits;
    }

    public String getTranslationKey() {
        return "condition." + WizardsAndBeastsMod.MODID + "." + condition.getId() + "." + id;
    }

    public String getDescriptionTranslationKey() {
        return getTranslationKey() + ".desc";
    }

    public static @Nullable ConditionOrigin byId(MagicalCondition condition, @Nullable String id) {
        if (id == null) {
            return null;
        }
        for (ConditionOrigin origin : values()) {
            if (origin.condition == condition && origin.id.equals(id)) {
                return origin;
            }
        }
        return null;
    }
}
