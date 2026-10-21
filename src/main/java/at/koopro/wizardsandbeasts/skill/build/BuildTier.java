package at.koopro.wizardsandbeasts.skill.build;

import java.util.Locale;

/** Where a node sits on a build's pathway, in the order a player meets it. */
public enum BuildTier {
    EARLY,
    MID,
    ADVANCED,
    CAPSTONE;

    /** {@code skill.wizards_and_beasts.build.tier.<name>} */
    public String translationKey() {
        return "skill.wizards_and_beasts.build.tier." + name().toLowerCase(Locale.ROOT);
    }
}
