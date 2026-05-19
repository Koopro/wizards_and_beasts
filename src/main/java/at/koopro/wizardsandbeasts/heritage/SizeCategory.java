package at.koopro.wizardsandbeasts.heritage;

public enum SizeCategory {
    SMALL("Small", 0.8f),
    NORMAL("Normal", 1.0f),
    LARGE("Large", 1.3f),
    HUGE("Huge", 1.8f);

    private final String displayName;
    private final float scaleFactor;

    SizeCategory(String displayName, float scaleFactor) {
        this.displayName = displayName;
        this.scaleFactor = scaleFactor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getScaleFactor() {
        return scaleFactor;
    }
}
