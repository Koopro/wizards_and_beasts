package at.koopro.wizardsandbeasts.ability;

import java.util.List;

import javax.annotation.Nullable;

/**
 * Catalogue of the beast forms an Animagus may assume. The form ids match
 * entries registered in {@link at.koopro.wizardsandbeasts.form.FormRegistry}
 * ({@code animagus_*}). Players choose one beast; the choice is stored in
 * {@code PlayerAbilityData.animagusFormId}.
 */
public final class AnimagusForms {

    private AnimagusForms() {}

    /** Ordered list of selectable beast forms (full form ids). */
    public static final List<String> IDS = List.of(
            "animagus_cat",
            "animagus_dog",
            "animagus_stag",
            "animagus_hawk",
            "animagus_hare",
            "animagus_beetle");

    /** Short beast keys (without the {@code animagus_} prefix) for command suggestions. */
    public static final List<String> BEAST_KEYS = List.of(
            "cat", "dog", "stag", "hawk", "hare", "beetle");

    /** The form a freshly-registered Animagus defaults to until they choose otherwise. */
    public static String defaultFormId() {
        return IDS.get(0);
    }

    public static boolean isAnimagusForm(@Nullable String formId) {
        return formId != null && IDS.contains(formId);
    }

    /** Resolves a beast key ({@code "stag"}) or full id ({@code "animagus_stag"}) to a full form id, or null. */
    @Nullable
    public static String resolve(@Nullable String beastOrId) {
        if (beastOrId == null) return null;
        if (IDS.contains(beastOrId)) return beastOrId;
        String prefixed = "animagus_" + beastOrId;
        return IDS.contains(prefixed) ? prefixed : null;
    }
}
