package at.koopro.wizardsandbeasts.type;

import at.koopro.wizardsandbeasts.form.FormRegistry;
import at.koopro.wizardsandbeasts.form.PlayerForm;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the type system (WizType, WizSubtype, TransformationState) to
 * form system identifiers. This is the only bridge between the two systems —
 * the form system itself has no knowledge of types.
 */
public final class TypeFormBridge {

    private TypeFormBridge() {}

    /**
     * Returns the default form ID for the given type/subtype/state combination.
     */
    public static String getDefaultFormId(WizType type, @Nullable WizSubtype subtype,
                                           TransformationState state) {
        return switch (type) {
            case WIZARDKIND -> "human_default";
            case WEREWOLF -> state == TransformationState.TRANSFORMED
                    ? "werewolf_wolf" : "werewolf_human";
            case OBSCURIAL -> state == TransformationState.TRANSFORMED
                    ? "obscurial_dark" : "obscurial_human";
            case GOBLIN -> "goblin_default";
            case HOUSE_ELF -> "house_elf_default";
            case VEELA -> state == TransformationState.TRANSFORMED
                    ? "veela_harpy" : "veela_human";
            case GIANT -> subtype != null && "full_giant".equals(subtype.getId())
                    ? "giant_default" : "half_giant_default";
            case CENTAUR -> "centaur_default";
            case VAMPIRE -> state == TransformationState.TRANSFORMED
                    ? "vampire_bat" : "vampire_human";
            case MERPEOPLE -> state == TransformationState.TRANSFORMED
                    ? "merfolk_water" : "merfolk_land";
        };
    }

    /**
     * Returns all form IDs available to the given type.
     */
    public static List<String> getAvailableFormIds(WizType type) {
        List<String> forms = new ArrayList<>();
        switch (type) {
            case WIZARDKIND -> forms.add("human_default");
            case WEREWOLF -> { forms.add("werewolf_human"); forms.add("werewolf_wolf"); }
            case OBSCURIAL -> { forms.add("obscurial_human"); forms.add("obscurial_dark"); }
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

    /**
     * Returns the default size profile ID for the given type/subtype/state.
     */
    public static String getDefaultSizeProfileId(WizType type, @Nullable WizSubtype subtype,
                                                   TransformationState state) {
        PlayerForm form = FormRegistry.get(getDefaultFormId(type, subtype, state));
        return form != null ? form.sizeProfileId() : "wizardkind_default";
    }
}
