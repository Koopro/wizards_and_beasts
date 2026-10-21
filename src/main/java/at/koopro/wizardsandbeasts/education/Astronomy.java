package at.koopro.wizardsandbeasts.education;

import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * What a telescope shows, and what charting it is worth. Pure.
 *
 * <p>Canon Astronomy is charting: the O.W.L. practical is filling in a star chart from the top of the Astronomy
 * Tower at night (OotP ch. 31). Here a candidate looks up through a telescope on a clear night and charts two
 * things that are true of this world's sky: the moon's phase (the same calendar the werewolves keep) and the
 * constellation riding highest that night. One observation per night, so the subject is earned by nights spent
 * outside, not by clicking — eight phases and twelve constellations, twenty marks in all.
 *
 * <p>The constellation follows the night count with a stride of five: five and twelve share no factor, so twelve
 * consecutive nights show all twelve, and the phase (a stride of one over eight) never locks step with it.
 */
@NullMarked
public final class Astronomy {

    /** Moon phases a telescope can tell apart. */
    public static final int PHASES = 8;
    private static final int STRIDE = 5;

    /** The twelve constellations this sky shows, one a night. */
    public enum Constellation {
        ORION, DRACO, URSA_MAJOR, CASSIOPEIA, LEO, SCORPIUS,
        CYGNUS, LYRA, ANDROMEDA, PERSEUS, CANIS_MAJOR, PEGASUS;

        public String nameKey() {
            return "education.constellation." + name().toLowerCase(Locale.ROOT);
        }

        /** A one-line fact the candidate reads when charting it — and may be asked on the written paper. */
        public String factKey() {
            return nameKey() + ".fact";
        }

        public int bit() {
            return 1 << ordinal();
        }
    }

    private Astronomy() {}

    /** The night a day-time belongs to: the evening of day {@code n} and the small hours after it are night n. */
    public static long night(long dayTime) {
        return Math.floorDiv(dayTime - 6000L, 24000L);
    }

    /** The constellation highest on night {@code night}. */
    public static Constellation constellationOn(long night) {
        Constellation[] all = Constellation.values();
        return all[(int) Math.floorMod(night * STRIDE, (long) all.length)];
    }

    public static int phaseBit(int phase) {
        return 1 << Math.floorMod(phase, PHASES);
    }

    /** Astronomy coursework: distinct phases observed plus distinct constellations charted. */
    public static int points(int moonPhases, int constellations) {
        return Integer.bitCount(moonPhases & 0xFF) + Integer.bitCount(constellations & 0xFFF);
    }

    /** Most marks the sky can give. */
    public static int maxPoints() {
        return PHASES + Constellation.values().length;
    }

    public static String phaseKey(int phase) {
        return "education.moon_phase." + Math.floorMod(phase, PHASES);
    }
}
