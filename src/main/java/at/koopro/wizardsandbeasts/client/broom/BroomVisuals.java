package at.koopro.wizardsandbeasts.client.broom;

import at.koopro.wizardsandbeasts.entity.broom.BroomEntity;
import at.koopro.wizardsandbeasts.entity.broom.BroomFlightRules;
import net.minecraft.util.Mth;
import org.jspecify.annotations.NullMarked;

/**
 * The hover bob, in one place, because two renderers have to draw it identically.
 *
 * <p>It used to live in the broom's animation clips as a {@code root} position track. That moved the broom
 * and not the rider sitting on it, so a hovering broom rose and fell through its rider's legs. Read by
 * {@link BroomRenderer} for the model and by {@link BroomRiderRenderer} for the rider — same broom, same
 * partial tick — it is one motion.
 */
@NullMarked
public final class BroomVisuals {

    /** Peak bob in blocks. A hover, not a bounce. */
    private static final float BOB_BLOCKS = 0.035f;
    /** Radians per tick: roughly a three-and-a-half-second cycle. */
    private static final float BOB_RATE = 0.09f;

    private BroomVisuals() {}

    /**
     * Vertical bob this frame, in blocks.
     *
     * <p>Fades out with speed: a broom in flight is carried by its motion, and a bob on top of that reads as
     * turbulence. Phased by entity id so a rack of parked brooms does not bob in lockstep.
     */
    public static float bob(BroomEntity broom, float partialTick) {
        float calm = 1.0f - BroomFlightRules.speedRatio(broom.getCurrentSpeed(), broom.getCruiseSpeed());
        return BOB_BLOCKS * calm * Mth.sin((broom.tickCount + partialTick) * BOB_RATE + broom.getId());
    }
}
