package at.koopro.wizardsandbeasts.chamber;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * The Chamber of Secrets quest, stage by stage. Each stage is reached by doing it in a Chamber (or, for the chart, by
 * being given one); none is granted by a menu or a milestone. The saved form is {@link #id()}.
 */
@NullMarked
public enum ChamberStage {
    /** A Serpent Chart was handed over: the player knows where a Chamber lies. */
    CHARTED,
    /** A hidden seal opened with the player standing by. */
    SEAL_OPENED,
    /** The serpent's gate rose with the player standing by. */
    GATE_OPENED,
    /** The player stood in the Chamber's hall. */
    HALL_ENTERED,
    /** The Chamber's basilisk died with the player fighting it or standing in its hall. */
    BASILISK_SLAIN,
    /** The Sword of Gryffindor came to the player in the Chamber. */
    SWORD_EARNED;

    private static final String KEY = "chamber.wizards_and_beasts.stage.";

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static @Nullable ChamberStage byId(String id) {
        for (ChamberStage stage : values()) {
            if (stage.id().equals(id)) {
                return stage;
            }
        }
        return null;
    }

    /** What reaching the stage reads as, in the toast. */
    public String reachedKey() {
        return KEY + id();
    }

    /** What to do next while this is the stage to reach. */
    public String hintKey() {
        return KEY + id() + ".hint";
    }

    /**
     * The stage to aim for next, given the stages reached: the first of seal, gate, hall and basilisk not yet reached.
     * Null before the quest has begun (nothing reached) and once the basilisk is slain: the sword is not a step one can
     * aim for, since only one exists in a world.
     */
    public static @Nullable ChamberStage next(Set<ChamberStage> reached) {
        if (reached.isEmpty() || reached.contains(BASILISK_SLAIN)) {
            return null;
        }
        for (ChamberStage stage : new ChamberStage[] {SEAL_OPENED, GATE_OPENED, HALL_ENTERED, BASILISK_SLAIN}) {
            if (!reached.contains(stage)) {
                return stage;
            }
        }
        return null;
    }
}
