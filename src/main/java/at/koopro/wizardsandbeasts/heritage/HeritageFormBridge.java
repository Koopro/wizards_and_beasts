package at.koopro.wizardsandbeasts.heritage;


import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the heritage system ({@link Heritage}, {@link HeritageVariant}, {@link TransformationState}) to
 * form system identifiers. This is the only bridge between the two systems —
 * the form system itself has no knowledge of heritage.
 */
public final class HeritageFormBridge {

    private HeritageFormBridge() {}

    /**
     * Returns the default form ID for the given heritage/variant/state combination.
     */
    public static String getDefaultFormId(Heritage heritage, @Nullable HeritageVariant variant,
                                           TransformationState state) {
        return getDefaultFormId(heritage, variant, null, state);
    }

    /**
     * The default form, with a condition taken into account: a werewolf or an Obscurial wears the condition's
     * forms whatever heritage they have, because the condition is what changes the body.
     */
    public static String getDefaultFormId(Heritage heritage, @Nullable HeritageVariant variant,
                                           @Nullable ConditionOrigin condition, TransformationState state) {
        boolean changed = state == TransformationState.TRANSFORMED;
        if (condition != null) {
            return switch (condition.condition()) {
                case LYCANTHROPY -> changed ? "werewolf_wolf" : "werewolf_human";
                case OBSCURUS -> changed ? "obscurial_dark" : "obscurial_human";
            };
        }
        return switch (heritage) {
            case WIZARDKIND -> "human_default";
            case GOBLIN -> "goblin_default";
            case HOUSE_ELF -> "house_elf_default";
            case VEELA -> state == TransformationState.TRANSFORMED
                    ? "veela_harpy" : "veela_human";
            case GIANT -> variant != null && "full_giant".equals(variant.getId())
                    ? "giant_default" : "half_giant_default";
            case CENTAUR -> "centaur_default";
            case VAMPIRE -> state == TransformationState.TRANSFORMED
                    ? "vampire_bat" : "vampire_human";
            case MERPEOPLE -> state == TransformationState.TRANSFORMED
                    ? "merfolk_water" : "merfolk_land";
        };
    }

    /**
     * Returns all form IDs available to the given heritage.
     */
    public static List<String> getAvailableFormIds(Heritage heritage) {
        return getAvailableFormIds(heritage, null);
    }

    /** Every form a heritage can wear, and, when a condition is carried, the condition's forms instead. */
    public static List<String> getAvailableFormIds(Heritage heritage, @Nullable ConditionOrigin condition) {
        List<String> forms = new ArrayList<>();
        if (condition != null) {
            switch (condition.condition()) {
                case LYCANTHROPY -> { forms.add("werewolf_human"); forms.add("werewolf_wolf"); }
                case OBSCURUS -> { forms.add("obscurial_human"); forms.add("obscurial_dark"); }
            }
            return forms;
        }
        switch (heritage) {
            case WIZARDKIND -> forms.add("human_default");
            case GOBLIN -> forms.add("goblin_default");
            case HOUSE_ELF -> forms.add("house_elf_default");
            case VEELA -> { forms.add("veela_human"); forms.add("veela_harpy"); }
            case GIANT -> { forms.add("giant_default"); forms.add("half_giant_default"); }
            case CENTAUR -> forms.add("centaur_default");
            case VAMPIRE -> { forms.add("vampire_human"); forms.add("vampire_bat"); }
            case MERPEOPLE -> { forms.add("merfolk_land"); forms.add("merfolk_water"); }
        }
        return forms;
    }
}
