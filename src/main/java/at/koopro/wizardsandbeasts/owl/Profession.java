package at.koopro.wizardsandbeasts.owl;

/**
 * The careers an O.W.L. sheet opens. What each one is for in play — the work it pays for and the
 * papers it carries — is {@link at.koopro.wizardsandbeasts.owl.career.CareerProfile}.
 *
 * <p>Constants are saved by name; none may be renamed or removed.
 */
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
    /** Not offered: its whole subject, Arithmancy, is not examined (see {@link OWLSubject#examined()}). */
    ARITHMANCER,
    HERBOLOGIST;

    /** Whether a candidate can choose this career at all. */
    public boolean offered() {
        return this != ARITHMANCER;
    }

    public String translationKey() {
        return "profession." + name().toLowerCase();
    }

    public String descriptionKey() {
        return "profession." + name().toLowerCase() + ".description";
    }
}
