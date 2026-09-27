package at.koopro.wizardsandbeasts.entity.creature;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;

/**
 * What an Acromantula says. Canon: they speak — Aragog to Harry and Ron, and his colony in the hollow ("Men! …
 * Fresh meat…"). No dialogue framework: a situation, a handful of lang lines each, spoken to one player through the
 * action bar on a long cooldown ({@code AcromantulaEntity#speak}). Words, not a truce — speaking does not make it
 * friendly.
 */
public final class AcromantulaSpeech {

    /** How far it will speak to someone it has not chosen as prey. */
    public static final double RANGE = 12.0;
    public static final int LINES = 3;

    public enum Situation {
        /** Someone in sight, outside its hollow. */
        WATCHING,
        /** Someone inside the colony's territory. */
        TRESPASS,
        /** Its prey. */
        HUNTING,
        /** Hurt and angry. */
        WOUNDED
    }

    private AcromantulaSpeech() {}

    public static Situation situation(boolean isPrey, boolean wounded, boolean inTerritory) {
        if (wounded) return Situation.WOUNDED;
        if (isPrey) return Situation.HUNTING;
        if (inTerritory) return Situation.TRESPASS;
        return Situation.WATCHING;
    }

    public static String key(Situation situation, int index) {
        return "entity.wizards_and_beasts.acromantula.speech." + situation.name().toLowerCase(java.util.Locale.ROOT)
                + "." + index;
    }

    public static Component line(Situation situation, RandomSource random) {
        return Component.translatable(key(situation, random.nextInt(LINES)));
    }
}
