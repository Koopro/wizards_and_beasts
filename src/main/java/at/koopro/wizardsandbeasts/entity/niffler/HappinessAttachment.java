package at.koopro.wizardsandbeasts.entity.niffler;

public final class HappinessAttachment {
    public static final float DEFAULT = 75.0f;
    public static final float MIN = 0.0f;
    public static final float MAX = 100.0f;

    private HappinessAttachment() {
    }

    public static float clamp(float value) {
        return Math.max(MIN, Math.min(MAX, value));
    }
}
