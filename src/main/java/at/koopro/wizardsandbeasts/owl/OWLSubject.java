package at.koopro.wizardsandbeasts.owl;

public enum OWLSubject {
    CHARMS,
    DEFENCE_AGAINST_DARK_ARTS,
    TRANSFIGURATION,
    POTIONS,
    HERBOLOGY,
    CARE_OF_MAGICAL_CREATURES,
    ARITHMANCY,
    ANCIENT_RUNES,
    DIVINATION,
    HISTORY_OF_MAGIC,
    ASTRONOMY,
    MUGGLE_STUDIES;

    public String translationKey() {
        return "owls.subject." + name().toLowerCase();
    }
}
