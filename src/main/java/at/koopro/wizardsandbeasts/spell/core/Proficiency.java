package at.koopro.wizardsandbeasts.spell.core;

public enum Proficiency {
    NOVICE(0, 1.0f, 1.0f),
    PROFICIENT(50, 0.85f, 1.10f),
    MASTERED(200, 0.70f, 1.20f);

    private final int castsRequired;
    private final float cooldownMultiplier;
    private final float damageMultiplier;

    Proficiency(int castsRequired, float cooldownMultiplier, float damageMultiplier) {
        this.castsRequired = castsRequired;
        this.cooldownMultiplier = cooldownMultiplier;
        this.damageMultiplier = damageMultiplier;
    }

    public int getCastsRequired() {
        return castsRequired;
    }

    public float getCooldownMultiplier() {
        return cooldownMultiplier;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public static Proficiency fromCastCount(int casts) {
        if (casts >= MASTERED.castsRequired) return MASTERED;
        if (casts >= PROFICIENT.castsRequired) return PROFICIENT;
        return NOVICE;
    }
}
