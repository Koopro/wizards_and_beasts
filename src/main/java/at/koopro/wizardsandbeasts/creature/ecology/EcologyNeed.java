package at.koopro.wizardsandbeasts.creature.ecology;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NullMarked;

import java.util.Locale;

/**
 * A drive a creature may have. Declared per species in its ecology profile ({@code "needs": {"hunger": 24000}}),
 * never given to every mob: a creature with no {@code needs} block has none and behaves exactly as before.
 *
 * <p>Every need is a pressure from 0 (satisfied) to 1 (desperate). The number in the profile is how many ticks it
 * takes to climb from 0 to 1 — or, for {@link #SAFETY}, how long a fright takes to fade. Needs are not player-facing
 * bars: {@link EcologyBrain} reads them, field notes describe the behaviour they cause, and the admin creature report
 * shows them for debugging.
 */
@NullMarked
public enum EcologyNeed implements StringRepresentable {
    /** Grows with time; eating (grazing, browsing, a kill, food on the ground) brings it down. */
    HUNGER(24000),
    /** Grows with time; drinking at water clears it. Never for an aquatic creature. */
    THIRST(12000),
    /** Tiredness: grows while awake, falls four times as fast while resting or asleep. */
    ENERGY(24000),
    /** Loneliness: grows away from its own kind, falls in their company. Social and herd species only. */
    SOCIAL(6000),
    /** Boredom: grows with time, cleared by investigating something. Curious species only. */
    CURIOSITY(6000),
    /** Alarm: jumps to 1 when hurt or threatened, then fades over this many ticks. */
    SAFETY(1200),
    /** Magical hunger: grows with time, fed by magic done nearby. Species with a magical sense only. */
    MAGIC(24000);

    public static final Codec<EcologyNeed> CODEC = StringRepresentable.fromEnum(EcologyNeed::values);

    private final int defaultTicks;

    EcologyNeed(int defaultTicks) {
        this.defaultTicks = defaultTicks;
    }

    /** Ticks from 0 to 1 when a profile names the need without a figure (archetypes may). */
    public int defaultTicks() {
        return defaultTicks;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
