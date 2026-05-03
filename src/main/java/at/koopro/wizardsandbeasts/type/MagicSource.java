package at.koopro.wizardsandbeasts.type;

public enum MagicSource {
    WAND("Wand"),
    INNATE("Innate"),
    HYBRID("Hybrid"),
    NONE("None");

    private final String displayName;

    MagicSource(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
