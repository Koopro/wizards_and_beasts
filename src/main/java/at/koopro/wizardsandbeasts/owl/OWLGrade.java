package at.koopro.wizardsandbeasts.owl;

public enum OWLGrade {
    O(6, true),
    E(5, true),
    A(4, true),
    P(3, false),
    D(2, false),
    T(1, false);

    public final int value;
    public final boolean passing;

    OWLGrade(int value, boolean passing) {
        this.value = value;
        this.passing = passing;
    }

    public String translationKey() {
        return "owls.grade." + name().toLowerCase();
    }
}
