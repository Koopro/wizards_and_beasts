package at.koopro.wizardsandbeasts.chamber;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * The basilisk encounter as rules, one Chamber at a time. Pure, so the transitions are tested without a world;
 * {@link ChamberHeartBlockEntity} applies them.
 *
 * <pre>
 * SLEEPING --someone stands in the hall--> AWAKE (the basilisk is woken)
 * AWAKE --the hall has stood empty for RESET_TICKS--> SLEEPING (the basilisk is put back: whole again next time)
 * AWAKE --the basilisk is gone without dying (unloaded, removed)--> SLEEPING
 * AWAKE --the basilisk dies--> SLAIN (for good)
 * </pre>
 */
@NullMarked
public final class ChamberEncounter {

    /** Two minutes of an empty hall and the fight is over: whoever comes next meets a whole basilisk. */
    public static final int RESET_TICKS = 20 * 120;

    public enum Phase implements StringRepresentable {
        SLEEPING, AWAKE, SLAIN;

        public static final Codec<Phase> CODEC = StringRepresentable.fromEnum(Phase::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** What the heart should do this look. */
    public enum Action { NOTHING, WAKE, PUT_BACK, FORGET }

    private ChamberEncounter() {}

    /**
     * @param anyoneInHall  a living, non-spectating player stands in the hall
     * @param basiliskThere the woken basilisk is loaded and alive
     * @param emptyTicks    how long the hall has stood empty while awake, this look included
     */
    public static Action decide(Phase phase, boolean moduleOn, boolean anyoneInHall, boolean basiliskThere,
                                int emptyTicks) {
        return switch (phase) {
            case SLAIN -> Action.NOTHING;
            case SLEEPING -> moduleOn && anyoneInHall ? Action.WAKE : Action.NOTHING;
            case AWAKE -> !basiliskThere ? Action.FORGET
                    : !anyoneInHall && emptyTicks >= RESET_TICKS ? Action.PUT_BACK
                    : Action.NOTHING;
        };
    }
}
