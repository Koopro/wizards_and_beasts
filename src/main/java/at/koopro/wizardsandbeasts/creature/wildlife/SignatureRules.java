package at.koopro.wizardsandbeasts.creature.wildlife;

import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.Optional;

/**
 * The decisions behind each species' signature behaviour, as pure functions: when an Augurey knows rain is coming,
 * what colour a Streeler is this hour, when a Cornish Pixie may snatch, when Skrewts turn on each other, what a
 * Griffin does about someone near its gold, what a Centaur reads in the sky, what a person hears of merfolk song, and
 * which shape a Boggart takes.
 *
 * <p>The companion of {@link WildlifeRules}, which holds the rules of the first wildlife pass. Same contract: no world,
 * no randomness, every rule a player can run into stated here so it can be tested and explained.
 */
@NullMarked
public final class SignatureRules {

    private SignatureRules() {}

    // ── the Augurey ────────────────────────────────────────────────────────

    /** How far ahead an Augurey hears rain coming: two in-game hours. */
    public static final int AUGUREY_FORECAST_TICKS = 2400;

    /**
     * Whether rain is on its way: the sky is clear now, the weather is free to change (not forced clear, the cycle
     * running), and the countdown to the next change is inside the forecast window.
     *
     * @param clearWeatherTime ticks of forced clear weather left ({@code /weather clear}); rain cannot come while positive
     * @param rainTime         ticks until the rain state next flips
     */
    public static boolean rainComing(boolean weatherCycles, boolean raining, int clearWeatherTime, int rainTime) {
        return weatherCycles && !raining && clearWeatherTime <= 0 && rainTime > 0 && rainTime <= AUGUREY_FORECAST_TICKS;
    }

    // ── the Streeler ───────────────────────────────────────────────────────

    /**
     * The colours a Streeler cycles through, one per hour. Saturated on purpose: it is kept as a pet for exactly this,
     * and the palette sits inside the report's "vivid by canon" exception rather than the 0.30–0.50 band.
     */
    public static final List<Integer> STREELER_COLOURS = List.of(
            0xFFC23B22, 0xFFE0782A, 0xFFE3C23A, 0xFF6FB83A, 0xFF2E9E8C, 0xFF3A6FC2, 0xFF6A3AC2, 0xFFB83A94);

    /** An in-game hour, in ticks. */
    public static final int HOUR_TICKS = 1000;

    /**
     * This hour's colour for a Streeler. {@code phase} staggers individuals so a pair is rarely the same colour, and
     * the colour holds for the whole hour: it changes on the hour, not gradually.
     */
    public static int streelerColour(long dayTime, int phase) {
        long hour = Math.floorDiv(dayTime, HOUR_TICKS) + phase;
        return STREELER_COLOURS.get((int) Math.floorMod(hour, STREELER_COLOURS.size()));
    }

    // ── the Cornish Pixie ──────────────────────────────────────────────────

    /** Pixies around one person before they stop pestering and start hoisting. */
    public static final int PIXIE_HOIST_SWARM = 3;

    /**
     * Whether a pixie may snatch what someone is holding: something in hand, a person who can lose it (not creative,
     * not a spectator), and the pixie not already carrying something.
     */
    public static boolean pixieMaySnatch(boolean somethingHeld, boolean canLoseItems, boolean alreadyCarrying) {
        return somethingHeld && canLoseItems && !alreadyCarrying;
    }

    /** Whether enough pixies are on someone to hoist them into the air. */
    public static boolean pixiesHoist(int pixiesNear) {
        return pixiesNear >= PIXIE_HOIST_SWARM;
    }

    // ── the Blast-Ended Skrewt ─────────────────────────────────────────────

    /** Skrewts close together (counting itself) before they start killing each other. */
    public static final int SKREWT_CROWD = 3;

    public static boolean skrewtsFight(int skrewtsNearIncludingSelf) {
        return skrewtsNearIncludingSelf >= SKREWT_CROWD;
    }

    // ── the Griffin ────────────────────────────────────────────────────────

    /** How close to a Griffin's hoard someone may come before it objects. */
    public static final double HOARD_RADIUS = 7.0;
    /** How long a Griffin remembers having warned someone off its hoard. */
    public static final int HOARD_WARNING_TICKS = 200;

    /**
     * What a Griffin does about someone near its gold: nothing, a warning, or an attack. The person who feeds it is
     * tolerated; anyone else is warned first and attacked if they stay after the warning.
     *
     * @param ticksSinceWarned ticks since this person was last warned off, or a negative number if never
     */
    public static WildlifeRules.Crowding hoardCrowding(boolean tolerated, double distanceToHoard, int ticksSinceWarned) {
        if (tolerated || distanceToHoard > HOARD_RADIUS) {
            return WildlifeRules.Crowding.NONE;
        }
        if (ticksSinceWarned >= 0 && ticksSinceWarned < HOARD_WARNING_TICKS) {
            return ticksSinceWarned > 40 ? WildlifeRules.Crowding.ATTACK : WildlifeRules.Crowding.NONE;
        }
        return WildlifeRules.Crowding.WARN;
    }

    // ── the Centaur ────────────────────────────────────────────────────────

    /** What a Centaur reads in the night sky. Each is true of the world at the moment it is read. */
    public enum Omen {
        /** A dangerous creature is abroad nearby ("Mars is bright tonight"). */
        MARS_BRIGHT,
        /** The moon is full: werewolves walk. */
        FULL_MOON,
        /** A storm is gathering. */
        STORM_COMING,
        /** Nothing a human needs to know. */
        SILENT
    }

    /**
     * The omen for tonight, most pressing first: danger abroad, then the full moon, then the weather. Centaurs do not
     * make things up for humans; when the sky has nothing to say, they say so.
     */
    public static Omen centaurOmen(boolean dangerAbroad, boolean fullMoon, boolean stormComing) {
        if (dangerAbroad) {
            return Omen.MARS_BRIGHT;
        }
        if (fullMoon) {
            return Omen.FULL_MOON;
        }
        return stormComing ? Omen.STORM_COMING : Omen.SILENT;
    }

    /** Whether a Centaur can read the sky here: night, open sky above, and no rain in the way. */
    public static boolean canStargaze(boolean night, boolean seesSky, boolean raining) {
        return night && seesSky && !raining;
    }

    /** How far a herd's forest reaches from a Centaur: harm done inside it is harm done to them. */
    public static final double CENTAUR_FOREST_RADIUS = 16.0;

    // ── merfolk song ───────────────────────────────────────────────────────

    /** What a person makes of merfolk singing: the song, a screech, or nothing. */
    public enum MerfolkSong { SONG, SCREECH, NONE }

    public static final double MERFOLK_SONG_RANGE = 24.0;
    public static final double MERFOLK_SCREECH_RANGE = 16.0;

    /**
     * Merfolk song carries under water and is only understood there; out of the water it is a screech, and it does
     * not carry as far.
     */
    public static MerfolkSong merfolkSong(boolean listenerEyesInWater, double distance) {
        if (listenerEyesInWater && distance <= MERFOLK_SONG_RANGE) {
            return MerfolkSong.SONG;
        }
        return distance <= MERFOLK_SCREECH_RANGE ? MerfolkSong.SCREECH : MerfolkSong.NONE;
    }

    // ── the Boggart ────────────────────────────────────────────────────────

    /** A shape a Boggart can take, and how frightening it is (the Ministry grade of the creature, 1–5). */
    public record Fear(String form, int dread) {}

    /**
     * The shape a Boggart takes for the one person facing it: the most frightening thing that person has met. Someone
     * who has met none of them gets nothing — the Boggart has nothing of theirs to become and stays a shapeless shadow.
     * Ties go to the earlier entry, so the choice is stable.
     */
    public static Optional<String> boggartForm(List<Fear> fearsMet) {
        Fear worst = null;
        for (Fear fear : fearsMet) {
            if (worst == null || fear.dread() > worst.dread()) {
                worst = fear;
            }
        }
        return worst == null ? Optional.empty() : Optional.of(worst.form());
    }

    /**
     * A Boggart facing more than one person cannot decide what to become, and loses its grip on all of them — which is
     * why you should never face one alone.
     */
    public static boolean boggartConfused(int watchers) {
        return watchers >= 2;
    }

    /** Light level a Boggart is comfortable in; brighter than this it withdraws. */
    public static final int BOGGART_MAX_LIGHT = 7;

    /** Whether a spot suits a Boggart: dark and roofed over, the way a wardrobe or a cupboard under the stairs is. */
    public static boolean boggartSettled(int light, boolean roofed) {
        return light <= BOGGART_MAX_LIGHT && roofed;
    }
}
