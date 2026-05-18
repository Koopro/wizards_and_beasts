package at.koopro.wizardsandbeasts.owl;

public enum Profession {
    AUROR,
    HEALER,
    WANDMAKER,
    MAGIZOOLOGIST,
    CURSE_BREAKER,
    POTIONEER,
    HIT_WIZARD,
    OBLIVIATOR,
    UNSPEAKABLE,
    PROFESSOR,
    QUIDDITCH_PLAYER,
    JOURNALIST,
    ARITHMANCER,
    HERBOLOGIST;

    public String translationKey() {
        return "profession." + name().toLowerCase();
    }

    public String descriptionKey() {
        return "profession." + name().toLowerCase() + ".description";
    }
}
