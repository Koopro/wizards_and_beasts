package at.koopro.wizardsandbeasts.form;

/**
 * The type of model used to render a player form.
 * <p>
 * {@code HUMANOID} uses the default player model with a swapped texture.
 * All other types use custom {@code EntityModel} subclasses with placeholder geometry.
 */
public enum ModelType {
    HUMANOID("Humanoid"),
    CUSTOM_BIPED("Custom Biped"),
    QUADRUPED("Quadruped"),
    SMALL_HUMANOID("Small Humanoid"),
    FLYING("Flying"),
    SWIMMING("Swimming"),
    SHADOW("Shadow"),
    /**
     * Antlered deer. Added last on purpose: anything syncing this enum by ordinal keeps its
     * existing numbering. The stag used to be {@link #QUADRUPED}, which renders the centaur
     * body — a stag Animagus came out as a horse-bodied biped.
     */
    STAG("Stag");

    private final String displayName;

    ModelType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
