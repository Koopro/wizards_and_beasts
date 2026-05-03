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
    SHADOW("Shadow");

    private final String displayName;

    ModelType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
