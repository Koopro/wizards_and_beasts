package at.koopro.wizardsandbeasts.ability;

import java.util.List;

import org.jspecify.annotations.Nullable;

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

    /** The first selectable form: a fallback for tooling, never what a player is given. */
    public static String defaultFormId() {
        return IDS.get(0);
    }

    /**
     * The one animal this witch or wizard becomes. An Animagus does not choose: the form is who they are —
     * McGonagall's cat, Sirius's dog, Pettigrew's rat, Skeeter's beetle — and it never changes. Drawn once from a
     * stable seed of the character's identity, among the forms that have a datapack definition (so the body it
     * gives has real physics), falling back to every selectable form if none do.
     *
     * <p>Every new Animagus used to become a cat, and could pick another by command
     * (documentation/CANON_AUDIT.md C-4). Choosing is now an operator tool only.
     */
    public static String innateFormId(java.util.UUID characterId) {
        List<String> defined = IDS.stream()
                .filter(id -> at.koopro.wizardsandbeasts.animagus.AnimagusFormBinding.resolve(id).isPresent())
                .toList();
        List<String> pool = defined.isEmpty() ? IDS : defined;
        long seed = characterId.getLeastSignificantBits() ^ Long.rotateLeft(characterId.getMostSignificantBits(), 29);
        return pool.get(new java.util.Random(seed).nextInt(pool.size()));
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
